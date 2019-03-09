package com.github.srmo.tgms.logger.ui;

import com.dslplatform.json.DslJson;
import com.dslplatform.json.runtime.Settings;
import com.github.srmo.tgms.logger.eventscan.EDScanEvent;
import com.github.srmo.tgms.logger.eventscan.LogScanner;
import com.github.srmo.tgms.logger.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class MainFrame {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final LogScanner logScanner;
    private JFrame frame;
    private JButton chooseButton;
    private JScrollPane scrollPane;
    private JTextArea textArea;
    private JButton startScanButton;
    private Path selectedPath;
    private Executor executor = Executors.newSingleThreadExecutor();
    private Consumer<Path> pathChooseCallback;

    //Step2 - Creating Components
    public void createComponents(Container pane) {
        chooseButton = new JButton(Constants.TEXT_CHOOSE_BUTTON);
        pane.add(chooseButton, BorderLayout.PAGE_START);
        chooseButton.addActionListener(action -> {
            System.out.println(action);
            selectedPath = selectFolder();
        });

        //Make the center component big, since that's the
        //typical usage of BorderLayout.
        textArea = new JTextArea();
        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(600, 300));
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        pane.add(scrollPane, BorderLayout.CENTER);

        startScanButton = new JButton(Constants.TEXT_SCAN_BUTTON);
        startScanButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                startScan();
            }
        });
        pane.add(startScanButton, BorderLayout.PAGE_END);

    }

    public void addText(String line) {
        textArea.append(line + "\n");
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }

    public MainFrame(String title) {
        UIManager.put("FileChooser.readOnly", Boolean.TRUE);
        DslJson<Object> dslJson = new DslJson<>(Settings.withRuntime()); //Runtime configuration needs to be explicitly enabled
        logScanner = new LogScanner(dslJson);
        this.frame = new JFrame("Frame");
        createComponents(frame.getContentPane());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

    }

    public Path selectFolder() {

        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (selectedPath != null) {
            fileChooser.setCurrentDirectory(selectedPath.toFile());
        }

        final int dialogResult = fileChooser.showDialog(frame, Constants.TEXT_FILECHOOSER_BUTTON);


        if (dialogResult == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (selectedFile != null) {
                if (!selectedFile.isDirectory()) {
                    //user might have clicked a file by accident. So let't assume he wants the file's parent directory
                    selectedFile = selectedFile.getParentFile();
                }
                selectedPath = selectedFile.toPath();
                pathChooseCallback.accept(selectedPath.toAbsolutePath());
                addText("You selected [" + selectedPath + "]");
            }
        } else {
            addText("You did not select a folder");
        }

        return selectedPath;
    }

    public void startScan() {
        executor.execute(() -> {
            final Set<EDScanEvent> edScanEvents = logScanner.extractScanEventsFromLogs(selectedPath, this::addText);
            logScanner.computeSystemBuckets(edScanEvents, this::addText);
        });
    }

    public boolean askForPermissionToCreateFolder(Path tgmsLoggerDir) {
        int answer = JOptionPane.showConfirmDialog(frame, "Do you allow creation of folder '" + tgmsLoggerDir + "'?");
        return answer == JOptionPane.YES_OPTION;

    }

    public void presentShutdown(String message) {
        JOptionPane.showMessageDialog(frame, message);
        frame.dispose();
    }

    public void setScandir(Path path) {
        selectedPath = path;
    }

    public void setPathChooseCallback(Consumer<Path> callback) {
        this.pathChooseCallback = callback;
    }
}
