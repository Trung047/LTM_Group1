package Logging;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SystemLogger {
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void info(String msg) {
        System.out.println("[" + now() + "] [INFO]  " + msg);
    }
    public static void warning(String msg) {
        System.out.println("[" + now() + "] [WARN]  " + msg);
    }
    public static void error(String msg) {
        System.err.println("[" + now() + "] [ERROR] " + msg);
    }
    private static String now() {
        return LocalDateTime.now().format(FMT);
    }
}
