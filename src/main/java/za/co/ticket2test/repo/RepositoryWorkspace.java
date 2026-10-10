package za.co.ticket2test.repo;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class RepositoryWorkspace implements AutoCloseable {
    private static final long MAX_ZIP = 12L * 1024 * 1024;
    private static final long MAX_EXTRACTED = 60L * 1024 * 1024;
    private static final int MAX_CONTEXT_CHARS = 180_000;
    private final Path tempRoot;
    private final Path projectRoot;
    private final List<Path> javaFiles;
    private final String context;

    private RepositoryWorkspace(Path tempRoot, Path projectRoot, List<Path> javaFiles, String context) {
        this.tempRoot = tempRoot; this.projectRoot = projectRoot; this.javaFiles = javaFiles; this.context = context;
    }

    public static RepositoryWorkspace fromBase64(String base64, String originalName) throws IOException {
        byte[] zip = Base64.getDecoder().decode(base64 == null ? "" : base64);
        if (zip.length == 0) throw new IOException("Attach a repository ZIP before verification.");
        if (zip.length > MAX_ZIP) throw new IOException("Repository ZIP exceeds 12 MB.");
        Path temp = Files.createTempDirectory("ticket2test-");
        Path extracted = temp.resolve("repository");
        Files.createDirectories(extracted);
        long total = 0;
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                Path out = extracted.resolve(e.getName()).normalize();
                if (!out.startsWith(extracted)) throw new IOException("Unsafe ZIP path detected.");
                Files.createDirectories(out.getParent());
                try (OutputStream os = Files.newOutputStream(out)) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = zin.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_EXTRACTED) throw new IOException("Expanded repository exceeds 60 MB.");
                        os.write(buf, 0, n);
                    }
                }
            }
        }
        Path root = findProjectRoot(extracted);
        List<Path> files = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.toString().contains(File.separator + "target" + File.separator))
                .sorted().forEach(files::add);
        }
        String context = buildContext(root, files);
        return new RepositoryWorkspace(temp, root, files, context);
    }

    private static Path findProjectRoot(Path extracted) throws IOException {
        try (var walk = Files.walk(extracted, 5)) {
            return walk.filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .min(Comparator.comparingInt(Path::getNameCount))
                    .map(Path::getParent)
                    .orElseThrow(() -> new IOException("No Maven pom.xml found in repository ZIP."));
        }
    }

    private static String buildContext(Path root, List<Path> files) throws IOException {
        StringBuilder b = new StringBuilder();
        Path pom = root.resolve("pom.xml");
        if (Files.exists(pom)) appendFile(b, root, pom);
        for (Path p : files) {
            if (b.length() >= MAX_CONTEXT_CHARS) break;
            appendFile(b, root, p);
        }
        if (b.length() > MAX_CONTEXT_CHARS) b.setLength(MAX_CONTEXT_CHARS);
        return b.toString();
    }

    private static void appendFile(StringBuilder b, Path root, Path p) throws IOException {
        String text = Files.readString(p, StandardCharsets.UTF_8);
        b.append("\n\n===== ").append(root.relativize(p).toString().replace('\\','/')).append(" =====\n");
        b.append(text, 0, Math.min(text.length(), 35_000));
    }

    public Path projectRoot() { return projectRoot; }
    public String context() { return context; }
    public int javaFileCount() { return javaFiles.size(); }

    public void writeGeneratedTest(String relativePath, String code) throws IOException {
        String safe = relativePath == null ? "" : relativePath.replace('\\','/');
        if (!safe.startsWith("src/test/java/") || !safe.endsWith(".java") || safe.contains(".."))
            throw new IOException("Generated test path is not allowed: " + safe);
        Path out = projectRoot.resolve(safe).normalize();
        if (!out.startsWith(projectRoot.resolve("src/test/java").normalize())) throw new IOException("Unsafe generated test path.");
        Files.createDirectories(out.getParent());
        Files.writeString(out, code == null ? "" : code, StandardCharsets.UTF_8);
    }

    @Override public void close() {
        try (var walk = Files.walk(tempRoot)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
