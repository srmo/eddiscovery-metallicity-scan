package com.github.srmo.tgms.logger;

import com.github.srmo.tgms.logger.ui.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final MainFrame mainFrame;
    private final String userHome = (String) System.getProperties().get("user.home");
    private Path tgmsLoggerDir = Paths.get(userHome, "tgms-logger");

    private Path tgmsPropertiesPath = tgmsLoggerDir.resolve("tgms-logger.properties");
    private TgmsProperties properties;
    private Path scanPath;


    public AppController(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    public void logUi(String text) {
        mainFrame.addText(text);
    }
    // TODO all those early returns are stupid. Refactor this into something better ... should have started with reactive right away
    public void doIt() {

        mainFrame.setPathChooseCallback((path) -> {
            this.scanPath = path;
            properties.put(TgmsProperties.KEY_EDLOGDIR, path.toAbsolutePath().toString());
            writeProperties();

        });
        logUi("Trying to find user settings...");
        logUi("... assuming program directory " + userHome);

        if (Files.exists(tgmsLoggerDir)) {
            logUi("... found previous tgms-com.github.tgms.logger directory [" + tgmsLoggerDir + "]");
        } else {
            boolean userAllowedCreation = mainFrame.askForPermissionToCreateFolder(tgmsLoggerDir);
            if (!userAllowedCreation) {
                mainFrame.presentShutdown("Cannot run without tgms directory. Shutting down");
                return;
            } else {
                try {
                    Files.createDirectory(tgmsLoggerDir);
                } catch (IOException e) {
                    log.error("Could not create required tgmsLogger Directory. [dir={}]", tgmsLoggerDir, e);
                    mainFrame.presentShutdown("Could not create required tgmsLogger Directory. [dir=" + tgmsLoggerDir + "; message=" + e.getMessage() + "]");
                    return;
                }
            }
        }
        tryLoadProperties();
        prepareProperties();

    }

    private void prepareProperties() {
        String edlogdir = (String) properties.get(TgmsProperties.KEY_EDLOGDIR);
        if (edlogdir == null) {
            logUi("... adding initial EDLOGDIR property to propertyfile");
            log.info("... adding initial EDLOGDIR property to propertyfile");

            properties.put(TgmsProperties.KEY_EDLOGDIR, "");
            writeProperties();
            edlogdir = "";
        }

        if (edlogdir.isBlank()) {
            logUi("");
            logUi("You need to select a LogDir!");
        } else {
            logUi("... assuming the Elite Dangerous events are stored in [edlogdir="+edlogdir+"]");
            logUi("... If this seems wrong, choose a different folder");
            logUi("... Otherwise you are good to click SCAN");
            mainFrame.setScandir(Paths.get(edlogdir));
        }
    }

    private void writeProperties() {
        try (OutputStream stream = Files.newOutputStream(tgmsPropertiesPath)) {
            properties.store(stream, null);
        } catch (IOException e) {
            log.error("Could not write properties file. [file={}]", tgmsPropertiesPath.toAbsolutePath(), e);
            mainFrame.presentShutdown("Could not write properties file. [file=" + tgmsPropertiesPath.toAbsolutePath() + "; message=" + e.getMessage() + "]");
        }
    }

    private void tryLoadProperties() {
        if (Files.exists(tgmsPropertiesPath)) {
            logUi("... loading previous properties");
        } else {
            logUi("... creating new properties");
            try {
                Files.createFile(tgmsPropertiesPath);
            } catch (IOException e) {
                log.error("Could not create required properties file. [file={}]", tgmsPropertiesPath.toAbsolutePath(), e);
                mainFrame.presentShutdown("Could not create required properties file. [file=" + tgmsPropertiesPath.toAbsolutePath() + "; message=" + e.getMessage() + "]");
                return;
            }
        }

        try (InputStream stream = Files.newInputStream(tgmsPropertiesPath)) {
            properties = new TgmsProperties();
            properties.load(stream);
        } catch (IOException e) {
            log.error("Could not load properties file. [file={}]", tgmsPropertiesPath.toAbsolutePath(), e);
            mainFrame.presentShutdown("Could not load properties file. [file=" + tgmsPropertiesPath.toAbsolutePath() + "; message=" + e.getMessage() + "]");
        }
    }
}
