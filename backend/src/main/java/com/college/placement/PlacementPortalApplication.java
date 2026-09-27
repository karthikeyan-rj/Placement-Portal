package com.college.placement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

@SpringBootApplication
public class PlacementPortalApplication {

    public static void main(String[] args) {
        loadDotenvIfPresent();
        SpringApplication.run(PlacementPortalApplication.class, args);
    }

    /**
     * Automatically loads key=value properties from .env or backend/.env into
     * System properties if they are not already set in the operating system environment.
     * This ensures local development runs (IDE, mvn, etc.) pick up Resend keys and
     * configuration seamlessly.
     */
    public static void loadDotenvIfPresent() {
        List<File> candidates = List.of(
                new File(".env"),
                new File("backend/.env"),
                new File("../backend/.env"),
                new File("../.env")
        );

        for (File file : candidates) {
            if (file.exists() && file.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int eq = line.indexOf('=');
                        if (eq <= 0) {
                            continue;
                        }
                        String key = line.substring(0, eq).trim();
                        String val = line.substring(eq + 1).trim();
                        if (val.length() >= 2 && ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'")))) {
                            val = val.substring(1, val.length() - 1);
                        }
                        if (System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, val);
                        }
                    }
                    break;
                } catch (IOException ignored) {
                }
            }
        }
    }
}
