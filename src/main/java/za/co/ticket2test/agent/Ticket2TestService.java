package za.co.ticket2test.agent;

import za.co.ticket2test.repo.RepositoryWorkspace;
import za.co.ticket2test.runner.MavenVerificationRunner;
import za.co.ticket2test.runner.MavenVerificationRunner.*;
import za.co.ticket2test.util.Json;
import java.util.*;

public final class Ticket2TestService {
    private final OpenAiTicket2TestAgent agent = new OpenAiTicket2TestAgent();
    private final MavenVerificationRunner runner = new MavenVerificationRunner();

    public Map<String,Object> verify(String ticket, String repositoryName, String repositoryBase64) throws Exception {
        return execute(ticket, repositoryName, repositoryBase64, false);
    }

    public Map<String,Object> generateMissingTests(String ticket, String repositoryName, String repositoryBase64) throws Exception {
        return execute(ticket, repositoryName, repositoryBase64, true);
    }

    public Map<String,Object> refineRequirement(String requirement, String message, String repositoryName, String repositoryBase64) throws Exception {
        if (requirement == null || requirement.isBlank()) throw new IllegalArgumentException("Add a requirement before using the copilot.");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Ask the copilot to clarify or edit the requirement.");
        if (!agent.configured()) throw new IllegalStateException("OPENAI_API_KEY is not configured. Export it before starting Ticket2Test.");
        try (RepositoryWorkspace ws = RepositoryWorkspace.fromBase64(repositoryBase64, repositoryName)) {
            return agent.refineRequirement(requirement.trim(), message.trim(), ws.context());
        }
    }

    private Map<String,Object> execute(String ticket, String repositoryName, String repositoryBase64, boolean generate) throws Exception {
        if (ticket == null || ticket.isBlank()) throw new IllegalArgumentException("Provide a business requirement to verify.");
        if (!agent.configured()) throw new IllegalStateException("OPENAI_API_KEY is not configured. Export it before starting Ticket2Test.");
        try (RepositoryWorkspace ws = RepositoryWorkspace.fromBase64(repositoryBase64, repositoryName)) {
            Map<String,Object> plan = generate
                    ? agent.generateMissingTests(ticket.trim(), ws.context())
                    : agent.analyse(ticket.trim(), ws.context());

            if (generate) {
                for (Object obj : Json.array(plan.get("generatedTests"))) {
                    Map<String,Object> g = Json.object(obj);
                    ws.writeGeneratedTest(Json.string(g.get("path")), Json.string(g.get("code")));
                }
            }

            RunResult run;
            try { run = runner.run(ws.projectRoot()); }
            catch (java.io.IOException e) {
                if (e.getMessage()!=null && e.getMessage().contains("Cannot run program \"mvn\""))
                    throw new IllegalStateException("Maven is not installed or not available on PATH. Install Maven or include mvnw in the repository.");
                throw e;
            }
            Map<String,Object> report = buildReport(ticket, repositoryName, ws.javaFileCount(), plan, run);
            report.put("mode", generate ? "GENERATED" : "BASELINE");
            return report;
        }
    }

    private static Map<String,Object> buildReport(String ticket,String repo,int files,Map<String,Object> plan,RunResult run) {
        Map<String,List<Map<String,Object>>> mappings=new HashMap<>();
        for(Object o:Json.array(plan.get("evidenceMappings"))){ Map<String,Object> m=Json.object(o); mappings.computeIfAbsent(Json.string(m.get("behaviourId")),k->new ArrayList<>()).add(m); }
        List<Object> behavioursOut=new ArrayList<>(); int verified=0, failed=0, missing=0, ambiguous=0, unexec=0;
        for(Object o:Json.array(plan.get("behaviours"))){
            Map<String,Object> b=Json.object(o); String id=Json.string(b.get("id")); String amb=Json.string(b.get("ambiguity"));
            List<Map<String,Object>> evidence=new ArrayList<>(); boolean anyPass=false, anyFail=false, anyMapped=false;
            for(Map<String,Object> m:mappings.getOrDefault(id,List.of())){
                anyMapped=true; String cls=Json.string(m.get("className")), method=Json.string(m.get("methodName"));
                TestResult tr=find(run.tests(),cls,method);
                Map<String,Object> e=new LinkedHashMap<>(m);
                e.put("status",tr==null?"NOT_EXECUTED":tr.status()); e.put("seconds",tr==null?0:tr.seconds()); e.put("message",tr==null?"No matching Surefire execution result was found.":tr.message());
                evidence.add(e); if(tr!=null&&"PASS".equals(tr.status()))anyPass=true; if(tr!=null&&("FAIL".equals(tr.status())||"ERROR".equals(tr.status())))anyFail=true;
            }
            String status;
            if(!amb.isBlank()){status="AMBIGUOUS";ambiguous++;}
            else if(anyFail){status="FAILED";failed++;}
            else if(anyPass){status="VERIFIED";verified++;}
            else if(!anyMapped){status="MISSING";missing++;}
            else {status="UNEXECUTABLE";unexec++;}
            Map<String,Object> bo=new LinkedHashMap<>(b); bo.put("status",status); bo.put("evidence",evidence); behavioursOut.add(bo);
        }
        int total=behavioursOut.size(); double behaviourCoverage=total==0?0:round2(verified*100d/total);
        Coverage c=run.coverage();
        List<Object> genOut=new ArrayList<>();
        for(Object o:Json.array(plan.get("generatedTests"))){ Map<String,Object> g=new LinkedHashMap<>(Json.object(o)); genOut.add(g); }
        String verdict = total>0 && verified==total ? "VERIFIED" : (failed>0 ? "FAILED" : "NOT_FULLY_VERIFIED");
        Map<String,Object> stats=new LinkedHashMap<>(); stats.put("total",total);stats.put("verified",verified);stats.put("failed",failed);stats.put("missing",missing);stats.put("ambiguous",ambiguous);stats.put("unexecutable",unexec);
        Map<String,Object> coverage=new LinkedHashMap<>();
        coverage.put("behaviour",behaviourCoverage);
        coverage.put("line",c.line());
        coverage.put("branch",c.branch());
        coverage.put("method",c.method());
        coverage.put("class",c.clazz());
        coverage.put("codeCoverageAvailable",c.available());
        coverage.put("source",c.source());
        Map<String,Object> out=new LinkedHashMap<>(); out.put("ticket",ticket);out.put("repositoryName",repo);out.put("title",plan.get("title"));out.put("summary",plan.get("summary"));out.put("testable",plan.get("testable"));out.put("questions",plan.get("questions"));out.put("behaviours",behavioursOut);out.put("generatedTests",genOut);out.put("stats",stats);out.put("coverage",coverage);out.put("verdict",verdict);out.put("mavenExitCode",run.exitCode());out.put("executionLog",run.log());out.put("javaFiles",files); return out;
    }

    private static double round2(double value) { return Math.round(value * 100d) / 100d; }

    private static TestResult find(Map<TestKey,TestResult> tests,String cls,String method){
        String wantedClass=normalizeClass(cls), wantedMethod=normalizeMethod(method);
        TestResult direct=tests.get(new TestKey(cls,method));
        if(direct!=null)return direct;
        for(var e:tests.entrySet()) {
            String actualClass=normalizeClass(e.getKey().className());
            String actualMethod=normalizeMethod(e.getKey().methodName());
            boolean classMatches=actualClass.equals(wantedClass)
                    || actualClass.endsWith("."+wantedClass)
                    || wantedClass.endsWith("."+actualClass)
                    || simpleName(actualClass).equals(simpleName(wantedClass));
            boolean methodMatches=actualMethod.equals(wantedMethod);
            if(classMatches && methodMatches) return e.getValue();
        }
        return null;
    }

    private static String normalizeClass(String value){
        return value==null?"":value.trim().replace('$','.');
    }

    private static String simpleName(String value){
        int i=value.lastIndexOf('.'); return i>=0?value.substring(i+1):value;
    }

    private static String normalizeMethod(String value){
        if(value==null)return "";
        String v=value.trim();
        int bracket=v.indexOf('['); if(bracket>0)v=v.substring(0,bracket);
        int paren=v.indexOf('('); if(paren>0)v=v.substring(0,paren);
        return v.trim();
    }
}
