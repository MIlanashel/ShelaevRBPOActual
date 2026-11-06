package ru.example.budget.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Loads local development settings before Spring Boot initializes its environment. */
public final class LocalEnvLoader {
    private LocalEnvLoader() {
    }

    public static void load() {
        Path file = Path.of(".env");
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Missing .env in the project directory. Copy .env to .env");
        }
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalArgumentException("Invalid .env entry (expected KEY=value)");
                }
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1).trim();
                if (!key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    throw new IllegalArgumentException("Invalid .env key: " + key);
                }
                if (value.length() >= 2 &&
                        ((value.startsWith("\"") && value.endsWith("\"")) ||
                         (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1);
                }
                // Real environment variables and explicitly set JVM properties take priority.
                if (System.getenv(key) == null && System.getProperty(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read .env from project directory", exception);
        }
    }
}
