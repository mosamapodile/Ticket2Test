package za.co.ticket2test.runner;

import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class MavenVerificationRunner {
    public record TestKey(String className, String methodName) {}
    public record TestResult(String status, double seconds, String message) {}
    public record Coverage(Double line, Double branch, Double method, Double clazz, boolean available, String source) {}
    public record RunResult(int exitCode, Map<TestKey,TestResult> tests, Coverage coverage, String log) {}

    public RunResult run(Path root) throws Exception {
        List<String> cmd = mavenCommand(root);
        cmd.add("-q");
        cmd.add("-DskipTests=false");
        // Keep the lifecycle moving to jacoco:report even when an assertion fails.
        // Compilation/infrastructure failures still stop Maven and are reported as unavailable coverage.
        cmd.add("-Dmaven.test.failure.ignore=true");
        cmd.add("org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent");
        cmd.add("test");
        cmd.add("org.jacoco:jacoco-maven-plugin:0.8.12:report");

        ProcessBuilder pb = new ProcessBuilder(cmd).directory(root.toFile()).redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder log = new StringBuilder();
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (log.length() < 100_000) log.append(line).append('\n');
                }
            } catch (IOException ignored) {}
        });
        reader.start();

        boolean finished = p.waitFor(180, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IOException("Maven verification timed out after 180 seconds.");
        }
        reader.join(3000);

        Map<TestKey,TestResult> tests = parseSurefire(root);
        int rawExit = p.exitValue();
        boolean assertionFailure = tests.values().stream()
                .anyMatch(t -> "FAIL".equals(t.status()) || "ERROR".equals(t.status()));
        int effectiveExit = rawExit != 0 ? rawExit : (assertionFailure ? 1 : 0);

        return new RunResult(effectiveExit, tests, parseCoverage(root), tail(log.toString(), 25_000));
    }

    private static List<String> mavenCommand(Path root) {
        Path mvnw = root.resolve("mvnw");
        if (Files.isRegularFile(mvnw)) {
            mvnw.toFile().setExecutable(true);
            return new ArrayList<>(List.of(mvnw.toAbsolutePath().toString()));
        }
        Path mvnwCmd = root.resolve("mvnw.cmd");
        if (Files.isRegularFile(mvnwCmd) && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return new ArrayList<>(List.of(mvnwCmd.toAbsolutePath().toString()));
        }
        return new ArrayList<>(List.of("mvn"));
    }

    private static Map<TestKey,TestResult> parseSurefire(Path root) {
        Map<TestKey,TestResult> out = new LinkedHashMap<>();
        try (var walk = Files.walk(root)) {
            List<Path> reports = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("TEST-") && p.getFileName().toString().endsWith(".xml"))
                    .filter(p -> p.toString().replace('\\','/').contains("/target/surefire-reports/"))
                    .sorted().toList();
            for (Path p : reports) parseSurefireFile(p, out);
        } catch (IOException ignored) {}
        return out;
    }

    private static void parseSurefireFile(Path p, Map<TestKey,TestResult> out) {
        try {
            Document doc = secureFactory().newDocumentBuilder().parse(p.toFile());
            NodeList cases = doc.getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                Element e = (Element) cases.item(i);
                String cls = e.getAttribute("classname");
                String method = e.getAttribute("name");
                double sec = parseDouble(e.getAttribute("time"));
                String status = "PASS", msg = "";
                NodeList failures = e.getElementsByTagName("failure");
                NodeList errors = e.getElementsByTagName("error");
                NodeList skipped = e.getElementsByTagName("skipped");
                if (failures.getLength() > 0) { status = "FAIL"; msg = nodeMessage(failures.item(0)); }
                else if (errors.getLength() > 0) { status = "ERROR"; msg = nodeMessage(errors.item(0)); }
                else if (skipped.getLength() > 0) status = "SKIPPED";
                out.put(new TestKey(cls, method), new TestResult(status, sec, msg));
            }
        } catch (Exception ignored) {}
    }

    private static String nodeMessage(Node n) {
        if (n instanceof Element e && !e.getAttribute("message").isBlank()) return e.getAttribute("message");
        String t = n == null ? "" : n.getTextContent();
        String stripped = t == null ? "" : t.strip();
        return stripped.substring(0, Math.min(stripped.length(), 800));
    }

    private static Coverage parseCoverage(Path root) {
        List<Path> reports = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            reports = walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals("jacoco.xml"))
                    .filter(p -> {
                        String normalized = p.toString().replace('\\','/');
                        return normalized.contains("/target/site/jacoco/");
                    })
                    .sorted().toList();
        } catch (IOException ignored) {}

        // Some multi-module builds only emit one aggregate report.
        if (reports.isEmpty()) {
            try (var walk = Files.walk(root)) {
                reports = walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().equals("jacoco.xml"))
                        .filter(p -> p.toString().replace('\\','/').contains("/target/site/jacoco-aggregate/"))
                        .sorted().limit(1).toList();
            } catch (IOException ignored) {}
        }

        if (reports.isEmpty()) return new Coverage(null, null, null, null, false, "JaCoCo report unavailable");

        Map<String,long[]> totals = new HashMap<>();
        int parsed = 0;
        for (Path report : reports) {
            try {
                Document doc = secureFactory().newDocumentBuilder().parse(report.toFile());
                Element rootElement = doc.getDocumentElement();
                NodeList children = rootElement.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element e && "counter".equals(e.getTagName())) {
                        String type = e.getAttribute("type");
                        long covered = parseLong(e.getAttribute("covered"));
                        long missed = parseLong(e.getAttribute("missed"));
                        long[] pair = totals.computeIfAbsent(type, k -> new long[2]);
                        pair[0] += covered;
                        pair[1] += missed;
                    }
                }
                parsed++;
            } catch (Exception ignored) {}
        }
        if (parsed == 0) return new Coverage(null, null, null, null, false, "JaCoCo report could not be parsed");

        return new Coverage(
                pct(totals.get("LINE")),
                pct(totals.get("BRANCH")),
                pct(totals.get("METHOD")),
                pct(totals.get("CLASS")),
                true,
                parsed == 1 ? "JaCoCo XML" : "JaCoCo XML · " + parsed + " modules"
        );
    }

    private static DocumentBuilderFactory secureFactory() {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); } catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); } catch (Exception ignored) {}
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        return f;
    }

    private static Double pct(long[] pair) {
        if (pair == null) return null;
        long covered = pair[0], missed = pair[1], total = covered + missed;
        if (total == 0) return 0d;
        return Math.round((covered * 10000d / total)) / 100d;
    }

    private static double parseDouble(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
    private static long parseLong(String s) { try { return Long.parseLong(s); } catch (Exception e) { return 0; } }
    private static String tail(String s, int max) { return s.length() <= max ? s : s.substring(s.length() - max); }
}
