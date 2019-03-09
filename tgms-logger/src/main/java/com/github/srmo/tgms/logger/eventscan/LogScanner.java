package com.github.srmo.tgms.logger.eventscan;

import com.dslplatform.json.DslJson;
import com.github.srmo.tgms.logger.ResultCallback;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;

public class LogScanner {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final DslJson<Object> dslJson;
    //private final Pattern scanEventPattern = Pattern.compile("^\"Scan\"|^\"FssAllBodiesFound\"");
    private final Pattern scanEventPattern = Pattern.compile("^\"" + EDScanEvent.Type.Scan + "\"|^\"" + EDScanEvent.Type.FSSAllBodiesFound + "\"");

    public LogScanner(DslJson<Object> dslJson) {
        this.dslJson = dslJson;
    }

    public boolean isScanEventLine(String line) throws ScanLineException {
        // pretty naive approach. But seems less expensive than parsing to json
        try {
            final String[] split = line.split("\"event\":");
            if (split.length != 2) {
                log.debug("line does not contain 1 'event' elements after split [line={}]", line);
                return false;
            } else {
                final String eventElement = split[1].trim();
                return scanEventPattern.matcher(eventElement).find();
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            log.error("line could not be split [line={}]", line);
            return false;
        }
    }

    public Set<EDScanEvent> extractScanEventsFromLogs(Path path, ResultCallback resultCallback) {

        PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:**.log");

        Stopwatch stopWatch = Stopwatch.createStarted();
        Set<EDScanEvent> eventSet = new HashSet<>();
        try {
            Files.list(path).forEach(currentFile -> {
                if (!pathMatcher.matches(currentFile)) {
                    resultCallback.stringResult("ignoring " + currentFile.getFileName());
                } else {
                    resultCallback.stringResult("processing " + currentFile.getFileName());
                    eventSet.addAll(scanFile(currentFile));
                }

            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        stopWatch.stop();
        resultCallback.stringResult("Scan took " + stopWatch.toString() + " and found " + eventSet.size() + " events.");
        return eventSet;


    }

    public void computeSystemBuckets(Set<EDScanEvent> scanEvents, ResultCallback resultCallback) {
        Stopwatch stopWatch = Stopwatch.createStarted();

        resultCallback.stringResult("Computing results ... please wait");

        // sanitize system names && find all FSSAllBodiesFoundEvents
        final Set<EDScanEvent> fssAllBodiesFoundEvents = scanEvents.stream().filter(edScanEvent -> {
            //  stupidly enough, FssAllBodiesScanned uses SystemName while Scan uses BodyName
            // either BodyName OR SystemName is populate, so copy the one that is set to be the "effectiveSystemName"
            // at this point, it's not the systemName for body scans but the body name, this will be fixed later on
            edScanEvent.calculateEffectiveSystemName();

            return edScanEvent.getEventType().equals(EDScanEvent.Type.FSSAllBodiesFound);
        }).collect(toSet());

        // scanEvents is now the set of all single scan events
        scanEvents.removeAll(fssAllBodiesFoundEvents);

        // flag all scan events if they have a FullScan event and extract all main-star scans
        final Set<EDScanEvent> mainStarScans = scanEvents.stream().filter(edScanEvent -> {
            if (fssAllBodiesFoundEvents.stream().anyMatch(fss -> fss.getEffectiveSystemName().endsWith(edScanEvent.getEffectiveSystemName()))) {
                edScanEvent.setBelongsToFullScan(true);
            }
            return edScanEvent.getEventType().equals(EDScanEvent.Type.Scan) && edScanEvent.getBodyID() == 0;

        }).collect(toSet());

        // scanEvents is now the set of all single scan events without main-stars
        scanEvents.removeAll(mainStarScans);

        // key=SystemName
        // value=List of body scans in this system for the main star
        final Map<String, List<EDScanEvent>> scansPerSystem = new HashMap<>();

        /*
        For each star system, based on the mainstar's name:
            - find all
         */
        mainStarScans.forEach(mainStarScan -> scansPerSystem.putAll( //
                // iterate over all body scans
                scanEvents.stream()
                        // find all body scans that belong to the main system
                        .filter(event -> event.getEffectiveSystemName().equals(mainStarScan.getEffectiveSystemName())) //
                        // set the body extractScanEventsFromLogs's effectiveSystemName to the mainSystem's name
                        // this fixes all body scans that know get the actual systemName they belong to
                        .peek(event -> {
                            event.setEffectiveSystemName(mainStarScan.getEffectiveSystemName());
                        }) //
                        // now group them in such a way, that all body scans are in a list for the respective system name
                        .collect(groupingBy(EDScanEvent::getEffectiveSystemName))));

        stopWatch.stop();
        resultCallback.stringResult("Computation done. Took " + stopWatch.toString());

        // if needed, you get the full bucket list on trace logging
        log.debug(scansPerSystem.toString().replace("EDScanEvent", "\n\tEDScanEvent").replace("], ", "], \n"));

        // now find all FGK Systems that have been full scanned
        resultCallback.stringResult("Scanning for fully scanned FGK systems...");
        final Set<EDScanEvent> fgkStars = mainStarScans.stream().filter(scan -> {
            switch (scan.getStarType()) {
                case "F":
                case "G":
                case "K":
                    if (scan.belongsToFullScan())
                        resultCallback.stringResult("System: " + scan.getEffectiveSystemName() + "; StarType: " + scan.getStarType());
                    return scan.belongsToFullScan;
                default:
                    return false;
            }
        }).collect(toSet());
        resultCallback.stringResult("Found " + fgkStars.size() + " FGK Systems that were fully scanned");
    }

    private Set<EDScanEvent> scanFile(Path f) {

        try {
            List<String> strings = Files.readAllLines(f);
            // parallelism deactivated. I still don't understand it very well. Used the test case to run it 100 times and it was 8 times slower with parallel streams
            // strings.stream().filter(this::isScanEventLine).forEach(this::extractScanEventFromLine);
            return strings.stream().filter(this::isScanEventLine).map(this::extractScanEventFromLine).collect(toSet());

        } catch (IOException | ScanLineException e) {
            throw new RuntimeException("Error while working on " + f, e);
        }
    }

    private EDScanEvent extractScanEventFromLine(String line) throws ScanLineException {
        try {
            byte[] bytes = line.getBytes(StandardCharsets.UTF_8.name());
            return dslJson.deserialize(EDScanEvent.class, bytes, bytes.length);
        } catch (IOException | IllegalArgumentException e) {
            throw new ScanLineException("foobar on line " + line, e);
        }
    }
}
