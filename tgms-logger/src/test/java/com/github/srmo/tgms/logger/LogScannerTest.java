package com.github.srmo.tgms.logger;

import com.dslplatform.json.DslJson;
import com.github.srmo.tgms.logger.eventscan.LogScanner;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.MockitoAnnotations.initMocks;

public class LogScannerTest {

    private static Path scanlogsDirectory;
    private final Logger log = LoggerFactory.getLogger(this.getClass());

    @Mock
    private DslJson<Object> dslJsonMock;

    private LogScanner classUnderTest;

    @BeforeClass
    public static void beforeClass() throws URISyntaxException {
        scanlogsDirectory = Paths.get(LogScannerTest.class.getClassLoader().getResource("scanlogs").toURI());
    }


    @Before
    public void setUp() {
        initMocks(this);
        assertThat(Files.exists(scanlogsDirectory), is(true));

        this.classUnderTest = new LogScanner(dslJsonMock);
    }

    /**
     * Only lines that split into two elements on a "event": literal and the second element
     * starts with {@link com.github.srmo.tgms.logger.eventscan.EDScanEvent.Type#Scan}
     * or {@link com.github.srmo.tgms.logger.eventscan.EDScanEvent.Type#FSSAllBodiesFound}
     */
    @Test
    public void testLinesShouldBeScanned() {
        final String rejectedWithoutEvent = "helloWorld";
        final String rejectedBlankLine = "";
        final String rejectedLineWithoutScanEvent = "\"Garbage\": \"and other stuff\", \"event\": \"appears again\"";
        final String acceptedScanEventLine = "\"Hello\"andotherstuff; \"event\": \"Scan\"";
        final String acceptedFssEventLine = "\"Hello\"andotherstuff; \"event\": \"FSSAllBodiesFound\"";

        assertThat(classUnderTest.isScanEventLine(rejectedWithoutEvent), is(false));
        assertThat(classUnderTest.isScanEventLine(rejectedBlankLine), is(false));
        assertThat(classUnderTest.isScanEventLine(rejectedLineWithoutScanEvent), is(false));
        assertThat(classUnderTest.isScanEventLine(acceptedScanEventLine), is(true));
        assertThat(classUnderTest.isScanEventLine(acceptedFssEventLine), is(true));
    }

    @Test
    public void testScanRoundTrip() {
        classUnderTest.extractScanEventsFromLogs(scanlogsDirectory, System.out::println);
    }
}
