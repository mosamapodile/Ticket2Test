package za.co.ticket2test.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import za.co.ticket2test.agent.Ticket2TestService;
import za.co.ticket2test.util.Json;
import za.co.ticket2test.repo.WorkspaceSessionManager;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Ticket2TestWebApp {
    private static final int DEFAULT_PORT = 8080;
    private static final int MAX_REQUEST_BYTES = 18 * 1024 * 1024;
    private static final Ticket2TestService SERVICE = new Ticket2TestService();
    private static final WorkspaceSessionManager WORKSPACES = new WorkspaceSessionManager();

    private Ticket2TestWebApp() {}

    public static void main(String[] args) throws IOException {
        int port = resolvePort();
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", Ticket2TestWebApp::serveIndex);
        server.createContext("/api/verify", Ticket2TestWebApp::verify);
        server.createContext("/api/generate-missing-tests", Ticket2TestWebApp::generateMissingTests);
        server.createContext("/api/health", Ticket2TestWebApp::health);
        server.createContext("/api/workspace/connect", Ticket2TestWebApp::connectWorkspace);
        server.createContext("/api/workspace/terminal", Ticket2TestWebApp::terminal);
        server.createContext("/api/workspace/archive", Ticket2TestWebApp::archiveWorkspace);
        server.createContext("/api/copilot/refine", Ticket2TestWebApp::refineRequirement);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("Ticket2Test is running at http://localhost:" + port);
        System.out.println(System.getenv("OPENAI_API_KEY") == null ? "OPENAI_API_KEY: not configured" : "OPENAI_API_KEY: configured");
        System.out.println("Press Ctrl+C to stop.");
    }

    private static int resolvePort() {
        String configured = System.getenv("T2T_PORT");
        if (configured == null || configured.isBlank()) return DEFAULT_PORT;
        try { return Integer.parseInt(configured); } catch (NumberFormatException ignored) { return DEFAULT_PORT; }
    }

    private static void serveIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange,405,Map.of("error","Method Not Allowed")); return; }
        String path=exchange.getRequestURI().getPath();
        if (!"/".equals(path) && !"/index.html".equals(path)) { sendJson(exchange,404,Map.of("error","Not Found")); return; }
        try(InputStream in=Ticket2TestWebApp.class.getResourceAsStream("/web/index.html")){
            if(in==null){sendJson(exchange,500,Map.of("error","UI resource missing"));return;}
            byte[] body=in.readAllBytes(); exchange.getResponseHeaders().set("Content-Type","text/html; charset=utf-8"); exchange.sendResponseHeaders(200,body.length); try(OutputStream out=exchange.getResponseBody()){out.write(body);} }
    }

    private static void health(HttpExchange exchange) throws IOException {
        sendJson(exchange,200,Map.of("status","ok","openaiConfigured",System.getenv("OPENAI_API_KEY")!=null));
    }

    private static void verify(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange,405,Map.of("error","Method Not Allowed")); return; }
        try {
            byte[] bytes=readLimited(exchange.getRequestBody(),MAX_REQUEST_BYTES);
            Map<String,Object> req=Json.object(Json.parse(new String(bytes,StandardCharsets.UTF_8)));
            Map<String,Object> report=SERVICE.verify(Json.string(req.get("ticket")),Json.string(req.get("repositoryName")),Json.string(req.get("repositoryBase64")));
            sendJson(exchange,200,report);
        } catch (IllegalArgumentException e) { sendJson(exchange,400,Map.of("error",e.getMessage())); }
          catch (IllegalStateException e) { sendJson(exchange,422,Map.of("error",e.getMessage())); }
          catch (Exception e) { e.printStackTrace(); sendJson(exchange,500,Map.of("error","Verification failed: "+safeMessage(e))); }
    }

    private static void generateMissingTests(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange,405,Map.of("error","Method Not Allowed")); return; }
        try {
            byte[] bytes=readLimited(exchange.getRequestBody(),MAX_REQUEST_BYTES);
            Map<String,Object> req=Json.object(Json.parse(new String(bytes,StandardCharsets.UTF_8)));
            Map<String,Object> report=SERVICE.generateMissingTests(Json.string(req.get("ticket")),Json.string(req.get("repositoryName")),Json.string(req.get("repositoryBase64")));
            sendJson(exchange,200,report);
        } catch (IllegalArgumentException e) { sendJson(exchange,400,Map.of("error",e.getMessage())); }
          catch (IllegalStateException e) { sendJson(exchange,422,Map.of("error",e.getMessage())); }
          catch (Exception e) { e.printStackTrace(); sendJson(exchange,500,Map.of("error","Test generation failed: "+safeMessage(e))); }
    }

    private static void connectWorkspace(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange,405,Map.of("error","Method Not Allowed")); return; }
        try { Map<String,Object> req=Json.object(Json.parse(new String(readLimited(exchange.getRequestBody(),64*1024),StandardCharsets.UTF_8)));
            var session=WORKSPACES.connect(Json.string(req.get("remoteUrl")));
            sendJson(exchange,200,Map.of("sessionId",session.id(),"remote",session.remote(),"tree",WORKSPACES.tree(session.id())));
        } catch(Exception e){ sendJson(exchange,400,Map.of("error",safeMessage(e))); }
    }

    private static void terminal(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange,405,Map.of("error","Method Not Allowed")); return; }
        try { Map<String,Object> req=Json.object(Json.parse(new String(readLimited(exchange.getRequestBody(),64*1024),StandardCharsets.UTF_8)));
            var result=WORKSPACES.terminal(Json.string(req.get("sessionId")),Json.string(req.get("command")));
            sendJson(exchange,200,Map.of("exitCode",result.exitCode(),"output",result.output(),"tree",WORKSPACES.tree(Json.string(req.get("sessionId")))));
        } catch(Exception e){ sendJson(exchange,400,Map.of("error",safeMessage(e))); }
    }

    private static void archiveWorkspace(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange,405,Map.of("error","Method Not Allowed")); return; }
        try { Map<String,Object> req=Json.object(Json.parse(new String(readLimited(exchange.getRequestBody(),64*1024),StandardCharsets.UTF_8))); String id=Json.string(req.get("sessionId"));
            sendJson(exchange,200,Map.of("repositoryName","ticket2test-remote.zip","repositoryBase64",WORKSPACES.archiveBase64(id)));
        } catch(Exception e){ sendJson(exchange,400,Map.of("error",safeMessage(e))); }
    }

    private static void refineRequirement(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { sendJson(exchange,405,Map.of("error","Method Not Allowed")); return; }
        try { Map<String,Object> req=Json.object(Json.parse(new String(readLimited(exchange.getRequestBody(),MAX_REQUEST_BYTES),StandardCharsets.UTF_8)));
            Map<String,Object> result=SERVICE.refineRequirement(Json.string(req.get("requirement")),Json.string(req.get("message")),Json.string(req.get("repositoryName")),Json.string(req.get("repositoryBase64")));
            sendJson(exchange,200,result);
        } catch(IllegalArgumentException e){sendJson(exchange,400,Map.of("error",e.getMessage()));} catch(IllegalStateException e){sendJson(exchange,422,Map.of("error",e.getMessage()));} catch(Exception e){sendJson(exchange,500,Map.of("error","Copilot failed: "+safeMessage(e)));}
    }

    private static byte[] readLimited(InputStream in,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n,total=0;
        while((n=in.read(buf))!=-1){total+=n;if(total>max)throw new IllegalArgumentException("Request exceeds the 18 MB limit.");out.write(buf,0,n);}return out.toByteArray();
    }
    private static String safeMessage(Exception e){String m=e.getMessage();return m==null||m.isBlank()?e.getClass().getSimpleName():m;}
    private static void sendJson(HttpExchange ex,int status,Object body)throws IOException{byte[] bytes=Json.stringify(body).getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");ex.getResponseHeaders().set("Cache-Control","no-store");ex.sendResponseHeaders(status,bytes.length);try(OutputStream out=ex.getResponseBody()){out.write(bytes);}}
}
