package sign;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Image;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;

/**
 * A utility class for downloading, reading, and writing resources.
 */
public class Signlink {

    private static final Logger LOGGER = Logger.getLogger(Signlink.class.getName());
    public static ClassLoader loader = ClassLoader.getSystemClassLoader();

    //region Unzip Methods
    public static void unzip(byte[] data, Path outputPath) throws IOException {
        if (data == null || data.length == 0) {
            throw new IOException("Cannot unzip empty or null data.");
        }
        try (InputStream in = new ByteArrayInputStream(data)) {
            unzip(in, outputPath);
        }
    }

    /**
     * Unzips the content of the given InputStream to the specified path.
     * Existing files are overwritten.
     */
    public static void unzip(InputStream in, Path outputPath) throws IOException {
        if (in == null) {
            throw new IOException("InputStream cannot be null.");
        }
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }

        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];

            while ((entry = zis.getNextEntry()) != null) {
                Path dst = outputPath.resolve(entry.getName());
                try (OutputStream out = Files.newOutputStream(
                        dst,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE))
                {
                    int read;
                    while ((read = zis.read(buffer)) > 0) {
                        out.write(buffer, 0, read);
                    }
                }
                zis.closeEntry();
            }
        }
    }
    //endregion

    //region Download Methods
    public static byte[] download(String url) throws IOException {
        return download(url, null);
    }

    /**
     * Downloads the content from the specified URL as a byte array, optionally notifying a listener of the progress.
     */
    public static byte[] download(String url, DownloadListener listener) throws IOException {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty.");
        }

        int size = getFileSize(url);
        if (size <= 0) {
            LOGGER.log(Level.WARNING, "Content length is unknown or zero for URL: {0}", url);
        }

        URL u = new URL(url);
        URLConnection conn = u.openConnection();
        conn.setRequestProperty("User-Agent", "Scape05/Launcher 1.0");
        conn.connect();

        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream baos = (size > 0)
                     ? new ByteArrayOutputStream(size)
                     : new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int totalRead = 0;
            int read;
            while ((read = in.read(buffer)) > 0) {
                baos.write(buffer, 0, read);
                totalRead += read;

                if (listener != null && size > 0) {
                    int percent = (int) ((totalRead * 100L) / size);
                    listener.onRead(percent);
                }
            }
            return baos.toByteArray();
        }
    }

    public static int getFileSize(String url) throws IOException {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be null or empty.");
        }

        HttpURLConnection httpConn = null;
        try {
            URLConnection conn = new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "Scape05/Launcher 1.0");

            if (conn instanceof HttpURLConnection) {
                httpConn = (HttpURLConnection) conn;
                httpConn.setRequestMethod("HEAD");
            }
            conn.connect();

            return conn.getContentLength();
        } finally {
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }
    }
    //endregion

    //region File I/O Helpers
    public static void write(String path, byte[] data) throws IOException {
        if (data == null) {
            throw new IllegalArgumentException("Data to write cannot be null.");
        }
        Files.write(
                getCachePath().resolve(path),
                data,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    public static byte[] read(String path) throws IOException {
        Path p = getCachePath().resolve(path);
        return Files.readAllBytes(p);
    }

    public static Path getPath(String file, Object... args) {
        if (file == null) {
            throw new IllegalArgumentException("File name cannot be null.");
        }
        String formatted = String.format(file, args).toLowerCase().replaceAll(" ", "_");
        return getCachePath().resolve(formatted);
    }

    public static InputStream getInputStream(String name) throws IOException {
        Path path = getPath(name);
        if (Files.exists(path)) {
            return Files.newInputStream(path, StandardOpenOption.READ);
        } else {
            // Try from classpath resources
            InputStream in = loader.getResourceAsStream(name.toLowerCase().replaceAll(" ", "_"));
            if (in == null) {
                throw new FileNotFoundException("Resource not found: " + name);
            }
            return in;
        }
    }

    public static byte[] getBytes(String name) throws IOException {
        try (InputStream in = getInputStream(name);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                baos.write(buffer, 0, read);
            }
            return baos.toByteArray();
        }
    }

    public static OutputStream getOutputStream(String name) throws IOException {
        return Files.newOutputStream(getPath(name));
    }
    //endregion

    //region Image/Font Helpers
    public static Image getImage(String name) throws IOException {
        return ImageIO.read(getInputStream("img/" + name));
    }

    public static Font getFont(String name) throws IOException, FontFormatException {
        return Font.createFont(Font.TRUETYPE_FONT, getInputStream("font/" + name));
    }
    //endregion

    //region Cache Initialization
    public static Path getCachePath() {
        return Paths.get(System.getProperty("user.home"), ".scape1");
    }

    static {
        Path path = getCachePath();
        try {
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            }
            Path libPath = path.resolve("lib");
            if (!Files.exists(libPath)) {
                Files.createDirectory(libPath);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error creating cache directories: {0}", e.getMessage());
        }
    }
    //endregion

    //region Inner Interfaces
    public interface DownloadListener {
        void onRead(int percent);
    }
    //endregion
}
