package scape05;

import sign.Signlink;

import javax.swing.*;
import javax.swing.plaf.basic.BasicProgressBarUI;
import java.awt.*;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;

/**
 * Responsible for updating and launching the Scape05 client.
 */
public class Updater implements Runnable {

    private static final Logger LOGGER = Logger.getLogger(Updater.class.getName());

    /**
     * We keep local config in a separate Properties object,
     * which we store in ~/.scape1/updater (by default).
     */
    private Properties localConfig = new Properties();

    private enum UpdateState {
        CLEANUP,
        FETCH_PROPERTIES,
        DOWNLOAD_FILES,
        VERIFY,
        STARTUP,
        FINISHED
    }

    private JFrame frame;
    private Progress progress = new Progress();
    private UpdateState currentState = UpdateState.FETCH_PROPERTIES;

    private Updater() {
        // Customize the progress bar UI
        progress.getProgressBar().setUI(new BasicProgressBarUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                g.setColor(Color.BLACK);
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
                g.setColor(new Color(140, 17, 17));
                g.drawRect(1, 1, c.getWidth() - 3, c.getHeight() - 3);

                int frac = (this.progressBar.getValue() << 8) / this.progressBar.getMaximum();
                int w = (c.getWidth() - 6) * frac >> 8;
                g.fillRect(3, 3, w, c.getHeight() - 6);
            }
        });

        frame = new JFrame("Scape05");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setResizable(false);
        frame.add(progress.getRootPanel());
        frame.pack();
        frame.setLocationRelativeTo(null);

        frame.setVisible(true);

        new Thread(this, "Updater").start();

        LOGGER.info("Updater initialized. UI is visible, and thread started.");
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (InstantiationException
                 | IllegalAccessException
                 | UnsupportedLookAndFeelException
                 | ClassNotFoundException ex) {
            LOGGER.log(Level.WARNING, "Failed to set system look and feel.", ex);
        }

        new Updater();
    }

    // --- UI helpers:
    private void setPercent(int percent) {
        progress.getProgressBar().setValue(percent);
    }

    private void setAction(String text) {
        progress.getActionLabel().setText(text);
    }

    /**
     * Loads or creates updater file.
     * This file stores "clientPropertiesUrl" so we can dynamically update it if the server changes.
     */
    private void loadLocalConfig() throws Exception {
        Path updaterPropsPath = Signlink.getPath("updater");
        if (Files.exists(updaterPropsPath)) {
            try (InputStream in = Files.newInputStream(updaterPropsPath)) {
                localConfig.load(in);
            }
            LOGGER.info("Loaded localConfig from " + updaterPropsPath);
        } else {
            // No existing file, create one with default URL
            localConfig.setProperty("clientPropertiesUrl", "https://devenir377.github.io/Scape05-Launcher/client.properties");
            saveLocalConfig();
            LOGGER.info("Created new updater with default URL.");
        }
    }

    private void saveLocalConfig() throws Exception {
        Path updaterPropsPath = Signlink.getPath("updater");
        try (OutputStream out = Files.newOutputStream(updaterPropsPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            localConfig.store(out, "Updater Bootstrap Properties");
        }
        LOGGER.info("Saved localConfig to " + updaterPropsPath);
    }

    @Override
    public void run() {
        Properties properties = new Properties();

        while (frame.isDisplayable()) {
            try {
                switch (currentState) {
                    case CLEANUP:
                        LOGGER.info("State: CLEANUP");
                        cleanup();
                        currentState = UpdateState.FETCH_PROPERTIES;
                        break;

                    case FETCH_PROPERTIES:
                        LOGGER.info("State: FETCH_PROPERTIES");
                        loadLocalConfig(); // ensure we have local config loaded
                        fetchProperties(properties); // fetch remote client.properties from localConfig's URL
                        currentState = UpdateState.DOWNLOAD_FILES;
                        break;

                    case DOWNLOAD_FILES:
                        LOGGER.info("State: DOWNLOAD_FILES");
                        downloadFiles(properties);
                        currentState = UpdateState.VERIFY;
                        break;

                    case VERIFY:
                        LOGGER.info("State: VERIFY");
                        verifyFiles(properties);
                        currentState = UpdateState.STARTUP;
                        break;

                    case STARTUP:
                        LOGGER.info("State: STARTUP");
                        startMainClass(properties);
                        currentState = UpdateState.FINISHED;
                        break;

                    case FINISHED:
                        LOGGER.info("State: FINISHED - disposing frame and exiting updater loop.");
                        frame.dispose();
                        return;
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "An error occurred in updater loop.", e);
                frame.dispose();
                JOptionPane.showMessageDialog(
                        null, e, "Error", JOptionPane.ERROR_MESSAGE
                );
                return;
            }
        }
    }

    /**
     * Helper method for cleaning up local files.
     */
    private void cleanup() throws Exception {
        LOGGER.fine("Cleaning up old files...");
        Files.deleteIfExists(Signlink.getPath("code.dat"));
        Files.deleteIfExists(Signlink.getPath("revision.txt"));
    }

    /**
     * Fetches remote properties from a URL defined in localConfig ("clientPropertiesUrl").
     * If the server includes "redirect-url" in client.properties, we update localConfig.
     */
    private void fetchProperties(Properties props) throws Exception {
        setAction("Fetching properties...");
        setPercent(10);

        // 1) Get the current URL from local config
        String urlForClientProps = localConfig.getProperty("clientPropertiesUrl");
        if (urlForClientProps == null || urlForClientProps.isEmpty()) {
            // fallback if localConfig is missing it
            urlForClientProps = "https://devenir377.github.io/Scape05-Launcher/client.properties";
            localConfig.setProperty("clientPropertiesUrl", urlForClientProps);
            saveLocalConfig();
        }

        LOGGER.info("Fetching remote properties from: " + urlForClientProps);
        props.clear();

        // 2) Download client.properties
        byte[] data = Signlink.download(urlForClientProps);
        try (ByteArrayInputStream in = new ByteArrayInputStream(data)) {
            props.load(in);
        }

        LOGGER.info("Properties fetched: " + props.stringPropertyNames());

        // 3) Check for "redirect-url" in the newly fetched props
        String redirectUrl = props.getProperty("redirect-url");
        if (redirectUrl != null && !redirectUrl.isEmpty()) {
            LOGGER.info("Found redirect-url in client.properties, updating localConfig to use: " + redirectUrl);
            localConfig.setProperty("clientPropertiesUrl", redirectUrl);
            saveLocalConfig();
        }
    }

    private void downloadFiles(Properties properties) throws Exception {
        Path codePath = Signlink.getPath("code.dat");
        Path revisionTxt = Signlink.getPath("revision.txt");

        String url = properties.getProperty("url");
        String revision = properties.getProperty("revision");

        byte[] jar;
        // If the files already exist, read them; otherwise download fresh
        if (Files.exists(codePath) && Files.exists(revisionTxt)) {
            LOGGER.info("Local client jar (code.dat) and revision.txt found. Skipping download...");
            jar = Files.readAllBytes(codePath);
        } else {
            // Download code jar
            setAction("Downloading game client...0%");
            setPercent(0);
            LOGGER.info("Downloading game client jar: " + url + revision + ".jar");
            jar = Signlink.download(url + revision + ".jar", (percent) -> {
                setPercent(percent);
                setAction("Downloading game client..." + percent + "%");
            });
            Files.write(codePath, jar, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // Download libraries, if enabled
            if (Boolean.parseBoolean(properties.getProperty("lib"))) {
                setAction("Downloading libraries...0%");
                setPercent(0);
                LOGGER.info("Downloading libraries: " + url + "lib.zip");
                byte[] libs = Signlink.download(url + "lib.zip", (percent) -> {
                    setPercent(percent);
                    setAction("Downloading libraries..." + percent + "%");
                });

                // Unzip them directly into the same cache directory
                Signlink.unzip(libs, Signlink.getCachePath());
            } else {
                LOGGER.info("Skipping library download (libraries=false).");
            }

            // Write revision file
            LOGGER.fine("Writing revision.txt with revision=" + revision);
            try (BufferedWriter writer = Files.newBufferedWriter(
                    revisionTxt,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                writer.write(revision);
            }
        }
    }

    private void verifyFiles(Properties properties) throws Exception {
        setAction("Verifying...");
        setPercent(70);

        LOGGER.info("Verifying downloaded files...");
        Thread.sleep(200L);

        Path codePath = Signlink.getPath("code.dat");
        Path revisionTxt = Signlink.getPath("revision.txt");

        // Verify jar with CRC
        byte[] jar = Files.readAllBytes(codePath);
        CRC32 crc = new CRC32();
        crc.update(jar);
        long actualCrc = crc.getValue();
        long expectedCrc = Long.parseLong(properties.getProperty("crc"));
        if (actualCrc != expectedCrc) {
            LOGGER.warning("CRC mismatch! " + actualCrc + " != " + expectedCrc + ". Forcing cleanup...");
            currentState = UpdateState.CLEANUP; // Force cleanup and retry
            return;
        }

        // Verify revision
        byte[] data = Files.readAllBytes(revisionTxt);
        String localRevision = new String(data, StandardCharsets.UTF_8);
        String serverRevision = properties.getProperty("revision");
        if (!localRevision.equals(serverRevision)) {
            LOGGER.warning("Revision mismatch! local=" + localRevision + ", server=" + serverRevision + ". Forcing cleanup...");
            currentState = UpdateState.CLEANUP;
        }
    }

    private void startMainClass(Properties properties) throws Exception {
        setAction("Starting up...");
        setPercent(100);

        LOGGER.info("Starting main class...");

        // Build a list of URLs to add to the class loader
        List<URL> libs = new ArrayList<>();

        // Add code.dat (the main client) to the classpath
        Path codeDat = Signlink.getPath("code.dat");
        if (Files.exists(codeDat)) {
            libs.add(codeDat.toUri().toURL());
            LOGGER.info("Added code.dat to class loader URLs.");
        } else {
            LOGGER.warning("code.dat not found! The main class may not be loadable.");
        }

        // Optionally, gather any .jar files in the same directory (if libraries were unzipped)
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                Signlink.getCachePath(), "*.jar")) {
            for (Path path : stream) {
                libs.add(path.toUri().toURL());
                LOGGER.info("Added JAR to class loader: " + path);
            }
        }

        // Create the class loader
        URLClassLoader loader = new URLClassLoader(libs.toArray(new URL[0]));
        Signlink.loader = loader;

        // Reflectively load the main class
        Class<?> mainClass = Class.forName(properties.getProperty("main-class"), true, loader);
        Method mainMethod = mainClass.getMethod("main", String[].class);

        // Build argument list from properties
        List<String> argsList = new ArrayList<>();
        for (String prop : properties.stringPropertyNames()) {
            argsList.add(properties.getProperty(prop));
        }

        LOGGER.info("Invoking main-class: " + mainClass.getName() + " with arguments: " + argsList);
        mainMethod.invoke(null, (Object) argsList.toArray(new String[0]));
    }
}
