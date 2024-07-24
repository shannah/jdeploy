package com.joshondesign.appbundler.mac;
import java.io.IOException;
import java.nio.file.*;

public class MacOSFileHandler {

    public static void copyOrExtract(String sourcePath, String destinationPath) throws IOException {
        if (!isMacOS()) {
            throw new UnsupportedOperationException("This function can only be executed on MacOS.");
        }

        Path source = Paths.get(sourcePath);
        Path destination = Paths.get(destinationPath);

        if (Files.exists(destination)) {
            throw new IOException("Destination path already exists: " + destinationPath);
        }

        if (Files.isDirectory(source)) {
            copyDirectory(source, destination);
        } else if (sourcePath.endsWith(".zip")) {
            extractZip(source, destination);
        } else if (sourcePath.endsWith(".tar.gz")) {
            extractTarGz(source, destination);
        } else {
            throw new IllegalArgumentException("Unsupported source path format: " + sourcePath);
        }

        rebaseIfNecessary(destination);
    }

    private static boolean isMacOS() {
        return System.getProperty("os.name").toLowerCase().contains("mac");
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("ditto", source.toString(), destination.toString());
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Failed to copy directory: " + source);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Copy interrupted", e);
        }
    }

    private static void extractZip(Path source, Path destination) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("ditto", "-xk", source.toString(), destination.toString());
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Failed to extract zip file: " + source);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Extraction interrupted", e);
        }
    }

    private static void extractTarGz(Path source, Path destination) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("tar", "-xzf", source.toString(), "-C", destination.toString());
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Failed to extract tar.gz file: " + source);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Extraction interrupted", e);
        }
    }

    private static void rebaseIfNecessary(Path destination) throws IOException {
        Path contentsPath = destination.resolve("Contents");
        Path binPath = destination.resolve("bin");
        if (!Files.exists(contentsPath) && !Files.exists(binPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(destination)) {
                for (Path entry : stream) {
                    if (Files.isDirectory(entry) && (Files.exists(entry.resolve("Contents")) || Files.exists(entry.resolve("bin")))) {
                        rebaseDirectory(destination, entry);
                        break;
                    }
                }
            }
        }
    }

    private static void rebaseDirectory(Path originalDestination, Path subdirectory) throws IOException {
        Path tempDir = Files.createTempDirectory("tempDestination");
        Files.deleteIfExists(tempDir);
        moveDirectory(originalDestination, tempDir);
        moveDirectory(tempDir.resolve(subdirectory.toFile().getName()), originalDestination);
        deleteDirectory(tempDir);
    }

    private static void moveDirectory(Path source, Path destination) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("mv", source.toString(), destination.toString());
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Failed to move directory: " + source);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Move interrupted", e);
        }
    }

    private static void deleteDirectory(Path path) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("rm", "-rf", path.toString());
        Process process = pb.start();
        try {
            if (process.waitFor() != 0) {
                throw new IOException("Failed to delete directory: " + path);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Delete interrupted", e);
        }
    }
}
