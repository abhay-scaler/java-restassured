package com.framework.utils;

import com.framework.exceptions.FrameworkException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Reads test data / schema files from the project's resources directories.
 * Uses plain file paths (not classpath lookups) so files are also easy to
 * browse/edit directly in the repo without a rebuild.
 */
public final class FileReaderUtils {

    private FileReaderUtils() {
    }

    public static String readAsString(String relativePath) {
        Path path = Paths.get(relativePath);
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FrameworkException("Failed to read file: " + path.toAbsolutePath(), e);
        }
    }

    public static Path resolve(String relativePath) {
        return Paths.get(relativePath).toAbsolutePath();
    }

    public static boolean exists(String relativePath) {
        return Files.exists(Paths.get(relativePath));
    }
}
