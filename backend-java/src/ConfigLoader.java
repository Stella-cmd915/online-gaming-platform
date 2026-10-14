import java.io.*;
import java.util.Properties;

/**
 * Utility class that loads application configuration from config.properties.
 * Each component (Master, Worker, Reducer, SRG) calls this at startup.
 * The file is expected to be in the current working directory.
 */
public class ConfigLoader {

    public static Properties load() throws IOException {
        Properties props = new Properties();
        File f = new File("config.properties");
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                props.load(fis);
            }
            System.out.println("[Config] Loaded config.properties");
        } else {
            System.err.println("[Config] WARNING: config.properties not found - using defaults");
        }
        return props;
    }
}
