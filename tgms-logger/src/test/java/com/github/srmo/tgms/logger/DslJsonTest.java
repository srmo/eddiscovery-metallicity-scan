package com.github.srmo.tgms.logger;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;
import com.github.srmo.tgms.logger.eventscan.EDScanEvent;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DslJsonTest {

    private static Path jsonPath;

    @BeforeClass
    public static void beforeClass() throws URISyntaxException {
         jsonPath = Paths.get(LogScannerTest.class.getClassLoader().getResource("ScanEvent.json").toURI());
    }

    @Test
    public void testParseScanEvent() throws IOException {
        //DslJson<Object> dslJson = new DslJson<>(Settings.withRuntime()); //Runtime configuration needs to be explicitly enabled
        DslJson<Object> dslJson = new DslJson<>(Settings.withRuntime().allowArrayFormat(true).includeServiceLoader());
        final String json = Files.readString(jsonPath);

        byte[] bytes = json.getBytes(StandardCharsets.UTF_8.name());
        final EDScanEvent deserialize = dslJson.deserialize(EDScanEvent.class, bytes, bytes.length);

        System.err.println(deserialize);


    }
}
