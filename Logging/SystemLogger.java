package Logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SystemLogger {
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void info(String message) {
        System.out.println("[" + LocalDateTime.now().format(formatter) + "] [INFO] " + message);
    }

    public static void warning(String message) {
        System.out.println("[" + LocalDateTime.now().format(formatter) + "] [WARN] " + message);
    }

    public static void error(String message) {
        System.err.println("[" + LocalDateTime.now().format(formatter) + "] [ERROR] " + message);
    }
}
