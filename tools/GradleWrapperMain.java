package org.gradle.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Properties;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Small bootstrap compatible with the Gradle wrapper entry point used by gradlew. */
public final class GradleWrapperMain {
    public static void main(String[] args) throws Exception {
        Path projectDir = findProjectDir(Paths.get(System.getProperty("user.dir")));
        Path propertiesFile = projectDir.resolve("gradle/wrapper/gradle-wrapper.properties");
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(propertiesFile)) {
            properties.load(input);
        }

        String distributionUrl = properties.getProperty("distributionUrl");
        if (distributionUrl == null || !distributionUrl.contains("gradle-")) {
            throw new IllegalStateException("Invalid distributionUrl in " + propertiesFile);
        }
        distributionUrl = distributionUrl.replace("\\:", ":");

        String fileName = distributionUrl.substring(distributionUrl.lastIndexOf('/') + 1);
        String distributionName = fileName.replaceAll("\\.zip$", "");
        String gradleVersion = distributionName.replaceFirst("-bin$", "");
        String gradleHomeName = gradleVersion;

        String gradleUserHomeValue = System.getenv("GRADLE_USER_HOME");
        Path gradleUserHome = gradleUserHomeValue == null || gradleUserHomeValue.isBlank()
                ? Paths.get(System.getProperty("user.home"), ".gradle")
                : Paths.get(gradleUserHomeValue);
        Path installParent = gradleUserHome.resolve("wrapper/dists").resolve(distributionName).resolve("channel-tube-bootstrap");
        Path gradleHome = installParent.resolve(gradleHomeName);
        Path gradleExecutable = gradleHome.resolve(isWindows() ? "bin/gradle.bat" : "bin/gradle");

        if (!Files.exists(gradleExecutable)) {
            Files.createDirectories(installParent);
            Path zipFile = installParent.resolve(fileName);
            System.out.println("Downloading official Gradle distribution: " + distributionUrl);
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(distributionUrl)).GET().build();
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(zipFile));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Gradle download failed with HTTP " + response.statusCode());
            }
            unzip(zipFile, installParent);
            Files.deleteIfExists(zipFile);
            if (!Files.exists(gradleExecutable)) {
                // Gradle archives contain a top-level directory whose name matches distributionName.
                throw new IOException("Could not find Gradle executable after extracting " + fileName);
            }
            if (!isWindows()) gradleExecutable.toFile().setExecutable(true);
        }

        ProcessBuilder builder;
        if (isWindows()) {
            builder = new ProcessBuilder("cmd", "/c", gradleExecutable.toString());
        } else {
            builder = new ProcessBuilder(gradleExecutable.toString());
        }
        for (String arg : args) builder.command().add(arg);
        builder.directory(projectDir.toFile());
        builder.inheritIO();
        Process process = builder.start();
        System.exit(process.waitFor());
    }

    private static Path findProjectDir(Path start) throws IOException {
        Path current = start.toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("gradle/wrapper/gradle-wrapper.properties"))) return current;
            current = current.getParent();
        }
        throw new IOException("Could not locate gradle/wrapper/gradle-wrapper.properties from " + start);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                Path output = targetDir.resolve(entry.getName()).normalize();
                if (!output.startsWith(targetDir.normalize())) {
                    throw new IOException("Invalid path in Gradle archive: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
    }
}
