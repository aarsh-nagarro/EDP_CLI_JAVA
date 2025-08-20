package tools;

import java.io.*;
import java.net.URL;
import java.nio.file.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.*;
import java.util.zip.GZIPInputStream;

public class BuildDistributions {
    private static final Logger LOGGER = Logger.getLogger(BuildDistributions.class.getName());

    // Correct Adoptium/TEMURIN release base (note %2B for '+')
    private static final String BASE_URL = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/";
    private static final List<String[]> TARGETS = List.of(
            new String[]{"windows", "OpenJDK21U-jre_x64_windows_hotspot_21.0.8_9.zip"},
            new String[]{"linux", "OpenJDK21U-jre_x64_linux_hotspot_21.0.8_9.tar.gz"},
            new String[]{"macos", "OpenJDK21U-jre_x64_mac_hotspot_21.0.8_9.tar.gz"}
    );

    public static void main(String[] args) throws Exception {
        Path projectRoot = Paths.get("").toAbsolutePath();
        Path distDir = projectRoot.resolve("dist");
        Path allDir = distDir.resolve("edp-cli-all");
        Files.createDirectories(allDir);

        // 🔹 Find the built JAR dynamically (supports version bumped by pipeline)
        Path jarFile = findBuiltJar(projectRoot.resolve("target"));
        String jarName = jarFile.getFileName().toString();

        for (String[] target : TARGETS) {
            String osName = target[0];
            String jreFile = target[1];
            String runtime = "runtime";

            Path osDir = distDir.resolve("edp-cli-" + osName); // temp build folder
            Files.createDirectories(osDir.resolve("bin"));
            Files.createDirectories(osDir.resolve("lib"));
            Files.createDirectories(osDir.resolve(runtime));

            // Copy CLI JAR
            Files.copy(jarFile, osDir.resolve("lib").resolve(jarName), StandardCopyOption.REPLACE_EXISTING);

            // Locate or download JRE
            Path preDownloadedJre = projectRoot.resolve("JRE").resolve(jreFile);
            Path jreArchive = distDir.resolve(jreFile);
            if (Files.exists(preDownloadedJre)) {
                String filesExist = "Using pre-downloaded JRE for " + osName + " from JRE folder...";
                LOGGER.log(Level.INFO, filesExist);
                Files.copy(preDownloadedJre, jreArchive, StandardCopyOption.REPLACE_EXISTING);
            } else if (!Files.exists(jreArchive)) {
                String filesDownload = "Downloading JRE for " + osName + "...";
                LOGGER.log(Level.INFO, filesDownload);
                try (InputStream in = new URL(BASE_URL + jreFile).openStream();
                     OutputStream out = Files.newOutputStream(jreArchive, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    in.transferTo(out);
                }
            } else {
                String jrePresent = "JRE archive already present for " + osName + ", skipping download.";
                LOGGER.log(Level.INFO, jrePresent);
            }

            // Extract JRE
            if (isDirectoryEmpty(osDir.resolve(runtime))) {
                String extractJRE = "Extracting JRE for " + osName + "...";
                LOGGER.log(Level.INFO, extractJRE);
                if (jreFile.endsWith(".zip")) {
                    unzip(jreArchive, osDir.resolve(runtime));
                } else {
                    untarGz(jreArchive, osDir.resolve(runtime));
                }
            }

            // Launcher scripts
            if ("windows".equals(osName)) {
                Path batFile = osDir.resolve("bin/edp-cli.bat");
                Files.writeString(batFile, "@echo off\r\n" +
                        "set DIR=%~dp0..\\\r\n" +
                        "\"%DIR%runtime\\jdk-21.0.8+9-jre\\bin\\java.exe\" -jar \"%DIR%lib\\" + jarName + "\" %*\r\n"
                );
            } else {
                Path shFile = osDir.resolve("bin/edp-cli");
                Files.writeString(shFile, "#!/bin/sh\n" +
                        "DIR=$(cd $(dirname $0)/.. && pwd)\n" +
                        "$DIR/runtime/jdk-21.0.8+9-jre/bin/java -jar $DIR/lib/" + jarName + " \"$@\"\n"
                );
                if (!shFile.toFile().setExecutable(true)) {
                    LOGGER.warning("Failed to set executable permission for " + shFile);
                }
            }

            // Zip into edp-cli-all/
            Path osZip = allDir.resolve("edp-cli-" + osName + ".zip");
            zipFolder(osDir, osZip);
            LOGGER.log(Level.INFO, "📦 Created: {0}", osZip.toAbsolutePath());

            // Clean up temp osDir
            deleteDirectoryRecursively(osDir);
        }

        // ✅ Also add shaded JAR into edp-cli-all
        Path allJar = allDir.resolve(jarName);
        Files.copy(jarFile, allJar, StandardCopyOption.REPLACE_EXISTING);
        LOGGER.log(Level.INFO, "📦 Added shaded JAR to: {0}", allJar.toAbsolutePath());

        LOGGER.log(Level.INFO, "✅ All distributions zipped inside: {0}", allDir.toAbsolutePath());
    }

    // 🔹 Find the shaded JAR dynamically
    private static Path findBuiltJar(Path targetDir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(targetDir, "edp-cli-*.jar")) {
            for (Path jar : stream) {
                if (Files.isRegularFile(jar)) {
                    return jar;
                }
            }
        }
        throw new FileNotFoundException("No JAR found in target/ matching edp-cli-*.jar");
    }

    private static void zipFolder(Path sourceFolder, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile));
             var paths = Files.walk(sourceFolder)) {
            paths.filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try (InputStream in = Files.newInputStream(path)) {
                            String relativePath = sourceFolder.getFileName() + "/" +
                                    sourceFolder.relativize(path).toString().replace(File.separator, "/");
                            zos.putNextEntry(new ZipEntry(relativePath));
                            in.transferTo(zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    private static void deleteDirectoryRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            try (var walk = Files.walk(path)) {
                walk.sorted((a, b) -> b.compareTo(a)) // delete children first
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {
                                throw new UncheckedIOException(ignored);
                            }
                        });
            }
        }
    }

    private static boolean isDirectoryEmpty(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            return !stream.iterator().hasNext();
        }
    }

    private static void unzip(Path zipFile, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path newFile = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(newFile);
                } else {
                    Files.createDirectories(newFile.getParent());
                    try (OutputStream out = Files.newOutputStream(newFile,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        zis.transferTo(out);
                    }
                }
            }
        }
    }

    private static void untarGz(Path tarGzFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (InputStream fi = Files.newInputStream(tarGzFile);
             GZIPInputStream gzi = new GZIPInputStream(fi);
             TarInputStream tis = new TarInputStream(gzi)) {
            TarEntry entry;
            while ((entry = tis.getNextEntry()) != null) {
                Path newFile = targetDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(newFile);
                } else {
                    Files.createDirectories(newFile.getParent());
                    try (OutputStream out = Files.newOutputStream(newFile,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                        tis.transferTo(out);
                    }
                }
            }
        }
    }

    // --- Minimal Tar classes ---
    private static class TarInputStream extends FilterInputStream {
        private final byte[] buf = new byte[512];
        private boolean eof = false;

        protected TarInputStream(InputStream in) { super(in); }

        TarEntry getNextEntry() throws IOException {
            if (eof) return null;
            int read = readFully(buf);
            if (read < 512) return null;
            boolean empty = true;
            for (byte b : buf) {
                if (b != 0) { empty = false; break; }
            }
            if (empty) { eof = true; return null; }
            String name = parseName(buf, 0, 100);
            long size = parseOctal(buf, 124, 12);
            boolean dir = buf[156] == '5';
            return new TarEntry(name, size, dir);
        }

        private int readFully(byte[] b) throws IOException {
            int total = 0;
            int read;
            while (total < b.length && (read = super.read(b, total, b.length - total)) != -1) {
                total += read;
            }
            return total;
        }

        private String parseName(byte[] buf, int offset, int length) {
            return new String(buf, offset, length).trim().replace("\0", "");
        }

        private long parseOctal(byte[] buf, int offset, int length) {
            String s = new String(buf, offset, length).trim();
            return s.isEmpty() ? 0 : Long.parseLong(s, 8);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return super.read(b, off, len);
        }
    }

    private static class TarEntry {
        final String name;
        final long size;
        final boolean directory;
        TarEntry(String name, long size, boolean directory) {
            this.name = name; this.size = size; this.directory = directory;
        }
        boolean isDirectory() { return directory; }
        String getName() { return name; }
    }
}
