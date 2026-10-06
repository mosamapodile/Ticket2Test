package za.co.ticket2test.repo;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Persistent, isolated development workspaces for Ticket2Test's repository console. */
public final class WorkspaceSessionManager {
    private static final long MAX_OUTPUT = 120_000;
    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

    public record Session(String id, Path root, String remote, Instant createdAt) {}
    public record CommandResult(int exitCode, String output) {}

    public Session connect(String remoteUrl) throws Exception {
        validateRemote(remoteUrl);
        String id = UUID.randomUUID().toString();
        Path base = Files.createTempDirectory("t2t-live-");
        Path root = base.resolve("repo");
        CommandResult result = runProcess(base, List.of("git","clone","--depth","1",remoteUrl,root.toString()), 90);
        if (result.exitCode()!=0) { delete(base); throw new IOException("Could not clone repository:\n"+result.output()); }
        Session s = new Session(id, root, remoteUrl, Instant.now()); sessions.put(id,s); return s;
    }

    public Session get(String id) {
        Session s=sessions.get(id); if(s==null) throw new IllegalArgumentException("Workspace session not found or expired."); return s;
    }

    public List<String> tree(String id) throws IOException {
        Path root=get(id).root(); List<String> out=new ArrayList<>();
        try(var walk=Files.walk(root,5)) { walk.filter(p->!p.equals(root)).filter(p->!p.toString().contains(File.separator+".git"+File.separator))
            .limit(220).forEach(p->out.add(root.relativize(p).toString().replace('\\','/')+(Files.isDirectory(p)?"/":""))); }
        return out;
    }

    public CommandResult terminal(String id, String raw) throws Exception {
        Session s=get(id); List<String> cmd=allowedCommand(raw); return runProcess(s.root(),cmd,120);
    }

    public String archiveBase64(String id) throws IOException {
        Path root=get(id).root(); ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(bytes); var walk=Files.walk(root)) {
            walk.filter(Files::isRegularFile).filter(p->!p.toString().contains(File.separator+".git"+File.separator))
                .filter(p->!p.toString().contains(File.separator+"target"+File.separator)).forEach(p->{try{
                    String name=root.getFileName()+"/"+root.relativize(p).toString().replace('\\','/'); zip.putNextEntry(new ZipEntry(name)); Files.copy(p,zip); zip.closeEntry();
                }catch(IOException e){throw new UncheckedIOException(e);}});
        } catch(UncheckedIOException e){throw e.getCause();}
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    private static void validateRemote(String value) {
        try { URI u=URI.create(value==null?"":value.trim()); if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null) throw new Exception();
            if(u.getUserInfo()!=null) throw new Exception();
        } catch(Exception e){ throw new IllegalArgumentException("Use a public HTTPS Git repository URL. Credentials in URLs are not allowed."); }
    }

    private static List<String> allowedCommand(String raw) {
        String c=raw==null?"":raw.trim(); if(c.isBlank()) throw new IllegalArgumentException("Enter a command.");
        if(c.matches("git\\s+status(\\s+--short)?")) return split(c);
        if(c.matches("git\\s+branch(\\s+(-a|--all))?")) return split(c);
        if(c.matches("git\\s+log(\\s+--oneline)?(\\s+-n\\s+\\d{1,2})?")) return split(c);
        if(c.matches("git\\s+diff(\\s+--stat)?")) return split(c);
        if(c.equals("pwd")) return List.of("pwd");
        if(c.equals("ls")||c.equals("ls -la")) return split(c);
        if(c.equals("mvn test")||c.equals("mvn -q test")) return split(c);
        if(c.equals("./mvnw test")||c.equals("./mvnw -q test")) return split(c);
        throw new IllegalArgumentException("Command blocked by the T2T workspace policy. Allowed: git status/branch/log/diff, pwd, ls, mvn test, ./mvnw test.");
    }
    private static List<String> split(String c){return new ArrayList<>(List.of(c.split("\\s+")));}
    private static CommandResult runProcess(Path dir,List<String> cmd,int seconds)throws Exception{
        ProcessBuilder pb=new ProcessBuilder(cmd).directory(dir.toFile()).redirectErrorStream(true); Process p=pb.start();
        ByteArrayOutputStream out=new ByteArrayOutputStream(); Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1&&out.size()<MAX_OUTPUT)out.write(b,0,Math.min(n,(int)MAX_OUTPUT-out.size()));}catch(IOException ignored){}}); reader.start();
        if(!p.waitFor(seconds,TimeUnit.SECONDS)){p.destroyForcibly();reader.join(1000);return new CommandResult(124,out.toString(StandardCharsets.UTF_8)+"\n[T2T] Command timed out.");}
        reader.join(1000); return new CommandResult(p.exitValue(),out.toString(StandardCharsets.UTF_8));
    }
    private static void delete(Path root){try(var w=Files.walk(root)){w.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(IOException ignored){}});}catch(IOException ignored){}}
}
