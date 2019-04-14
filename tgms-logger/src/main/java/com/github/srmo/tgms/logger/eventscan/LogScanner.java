package com.github.srmo.tgms.logger.eventscan;

import com.dslplatform.json.DslJson;
import com.github.srmo.tgms.logger.ResultCallback;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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

    public void computeSystemBuckets(Set<EDScanEvent> scanEvents, ResultCallback resultCallback, Path exportPath) {
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

        // find all main-star scans
        final Set<EDScanEvent> mainStarScans = scanEvents.stream()//
                .filter(edScanEvent -> //
                        edScanEvent.getEventType().equals(EDScanEvent.Type.Scan) //
                                && edScanEvent.getDistanceFromArrival() == 0.0 //
                                && edScanEvent.getStarType() != null //
                ).collect(toSet());
        // scanEvents is now the set of all single scan events without main-stars
        scanEvents.removeAll(mainStarScans);

        // flag all scan events if they have a FullScan event and re-compute the effective systemname
        for (EDScanEvent mainStarScan : mainStarScans) {
            final String mainStarName = mainStarScan.getEffectiveSystemName();
            final Optional<EDScanEvent> fssEvent =
                    fssAllBodiesFoundEvents.stream().filter(fss -> mainStarName.startsWith(fss.getEffectiveSystemName())).findAny();

            // for FSSAllBodiesFound events the star's systemName is trivially the fss.systemName
            if (fssEvent.isPresent()) {
                mainStarScan.setEffectiveSystemName(fssEvent.get().getEffectiveSystemName());
                mainStarScan.setBelongsToFullScan(true);
            } else if (mainStarScan.getBodyID() == 0) {
                // if the starScan has a bodyId of 0, it is a single star system and the bodyName is actually the systemName
                mainStarScan.setEffectiveSystemName(mainStarScan.getBodyName());
                mainStarScan.setBelongsToFullScan(false);
            } else if (mainStarName.endsWith(" A")){
                // we assume that all primary scans from a multi-star system have a " A" suffix as BodyName, i.e. a 2 character suffix
                // we therefor assume that the correct systemName is the BodyName without those 2 chars
                final String computedMainStarName = mainStarName.substring(0, mainStarName.length() - 2);
                mainStarScan.setEffectiveSystemName(computedMainStarName);
                mainStarScan.setBelongsToFullScan(false);
            }

        }

        // key=SystemName
        // value=List of body scans in this system for the main star
        final Map<String, List<EDScanEvent>> scansPerSystem = new HashMap<>();

        mainStarScans.forEach(mainStarScan -> scansPerSystem.putAll( //
                // iterate over all body scans
                scanEvents.stream()
                        // find all body scans that belong to the main system
                        .filter(bodyScanEvent -> bodyScanEvent.getEffectiveSystemName().startsWith(mainStarScan.getEffectiveSystemName())) //
                        // make sure the bodyScans now point to the sanitized systemName from their mainStar
                        .peek(bodyScanEvent -> bodyScanEvent.setEffectiveSystemName(mainStarScan.getEffectiveSystemName())) //
                        // now group them in such a way, that all body scans are in a list for the respective system name
                        .collect(groupingBy(EDScanEvent::getEffectiveSystemName))));

        stopWatch.stop();
        resultCallback.stringResult("Computation done. Took " + stopWatch.toString());

        // if needed, you get the full bucket list on trace logging
        log.trace(scansPerSystem.toString().replace("EDScanEvent", "\n\tEDScanEvent").replace("], ", "], \n"));

        // now find all FGK Systems that have been full scanned
        resultCallback.stringResult("Scanning for fully scanned FGK systems...");

        Path fullScanLogPath = exportPath.resolve("fullscan.log");
        Path partialScansWithGas = exportPath.resolve("partialScansWithGas.log");
        try {
            if (!Files.exists(fullScanLogPath)) {
                Files.createFile(fullScanLogPath);
            } else {
                Files.writeString(fullScanLogPath, "", StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Could not create file for full scan export.", e);
            resultCallback.stringResult("Could not create file for full scan export. [message=" + e.getMessage() + "]");
            return;
        }
        try {
            if (!Files.exists(partialScansWithGas)) {
                Files.createFile(partialScansWithGas);
            } else {
                Files.writeString(partialScansWithGas, "", StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            log.error("Could not create file for partial scan export.", e);
            resultCallback.stringResult("Could not create file for partial scan export. [message=" + e.getMessage() + "]");
            return;
        }

        final Set<EDScanEvent> fgkStars = mainStarScans.stream().filter(scan -> {
            switch (scan.getStarType()) {
                case "F":
                case "G":
                case "K":
                    if (scan.belongsToFullScan()) {
                        try {
                            Files.writeString(fullScanLogPath, scan.getEffectiveSystemName(), StandardOpenOption.APPEND);
                            Files.writeString(fullScanLogPath, "\n", StandardOpenOption.APPEND);
                            resultCallback.stringResult("FullScan: YES; System: " + scan.getEffectiveSystemName() + "; StarType: " + scan.getStarType());
                        } catch (IOException e) {
                            log.error("could not add full scan entry to logfile.", e);
                            resultCallback.stringResult(
                                    "Could not add full scan entry to logfile. [system=" + scan.getEffectiveSystemName() + "; message=" + e.getMessage() + "]");
                        }
                    }
                    return scan.belongsToFullScan;
                default:
                    return false;
            }
        }).collect(toSet());
        resultCallback.stringResult("Found " + fgkStars.size() + " FGK Systems that were fully scanned");

        resultCallback.stringResult("Scanning for NOT fully FGK systems where a Gas Giant was found...");
        final Set<EDScanEvent> fgkGasStars = mainStarScans.stream().filter(scan -> {
            switch (scan.getStarType()) {
                case "F":
                case "G":
                case "K":
                    if (!scan.belongsToFullScan()) {
                        final List<EDScanEvent> bodyScans = scansPerSystem.get(scan.getEffectiveSystemName());
                        if (bodyScans == null) {
                            return false;
                        } else if (bodyScans.stream().anyMatch(EDScanEvent::isGasBody)) {
                            try {
                                Files.writeString(partialScansWithGas, scan.getEffectiveSystemName(), StandardOpenOption.APPEND);
                                Files.writeString(partialScansWithGas, "\n", StandardOpenOption.APPEND);
                                resultCallback.stringResult(
                                        "FullScan: NO; Has gas: YES; System: " + scan.getEffectiveSystemName() + "; StarType: " + scan.getStarType());
                            } catch (IOException e) {
                                log.error("could not add partial scan entry to logfile.", e);
                                resultCallback.stringResult(
                                        "Could not add partial scan entry to logfile. [system=" + scan.getEffectiveSystemName() + "; message=" + e
                                                .getMessage() + "]");
                            }
                            return true;
                        } else {
                            return false;
                        }
                    }
                default:
                    return false;
            }
        }).collect(toSet());
        resultCallback.stringResult("Found " + fgkGasStars.size() + " FGK Systems that were NOT fully scanned but had a gas body scanned");
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
