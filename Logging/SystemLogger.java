package Logging;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

/**
 * Ghi log ra console (nhu truoc) VA ra file, theo duong dan cau hinh
 * trong config.properties (key: log.file, mac dinh: logs/server.log).
 *
 * Neu khong doc duoc config hoac khong mo duoc file (vd: thu muc read-only),
 * SystemLogger van hoat dong binh thuong — chi bo qua phan ghi file,
 * KHONG lam crash server.
 */
public class SystemLogger {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Object  FILE_LOCK = new Object();
    private static       PrintWriter fileWriter = null;

    static {
        String logFile = "logs/server.log";
        try (FileInputStream is = new FileInputStream("config.properties")) {
            Properties props = new Properties();
            props.load(is);
            logFile = props.getProperty("log.file", logFile);
        } catch (Exception ignored) {
            // Khong co config.properties o working directory -> dung duong dan mac dinh
        }

        try {
            Path path = Paths.get(logFile);
            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);
            fileWriter = new PrintWriter(new FileWriter(path.toFile(), true), true);
        } catch (IOException e) {
            System.err.println("[SystemLogger] Khong the mo file log '" + logFile
                    + "' — chi ghi ra console. Ly do: " + e.getMessage());
            fileWriter = null;
        }
    }

    public static void info(String msg)    { write("[INFO]  " + msg, false); }
    public static void warning(String msg) { write("[WARN]  " + msg, false); }
    public static void error(String msg)   { write("[ERROR] " + msg, true);  }

    private static void write(String line, boolean toStderr) {
        String full = "[" + now() + "] " + line;
        if (toStderr) System.err.println(full);
        else          System.out.println(full);

        synchronized (FILE_LOCK) {
            if (fileWriter != null) {
                fileWriter.println(full);
            }
        }
    }

    private static String now() {
        return LocalDateTime.now().format(FMT);
    }
}
