package za.co.ticket2test.agent;

import za.co.ticket2test.util.Json;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public final class OpenAiTicket2TestAgent {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final String apiKey = System.getenv("OPENAI_API_KEY");
    private final String model = Optional.ofNullable(System.getenv("T2T_OPENAI_MODEL")).filter(s -> !s.isBlank()).orElse("gpt-5.6");

    public boolean configured() { return apiKey != null && !apiKey.isBlank(); }

    public Map<String,Object> analyse(String ticket, String repositoryContext) throws Exception {
        return call(ticket, repositoryContext, false);
    }

    public Map<String,Object> generateMissingTests(String ticket, String repositoryContext) throws Exception {
        return call(ticket, repositoryContext, true);
    }


    public Map<String,Object> refineRequirement(String requirement, String message, String repositoryContext) throws Exception {
        if (!configured()) throw new IllegalStateException("OPENAI_API_KEY is not configured on the Ticket2Test server.");
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", model); body.put("store", false);
        body.put("instructions", "You are Ticket2Test Requirements Copilot. Help a QA engineer refine a software requirement without inventing decisions. Preserve existing intent, apply explicit user edits, identify unresolved ambiguity, and return concise structured output.");
        body.put("input", "CURRENT REQUIREMENT:\n"+requirement+"\n\nUSER MESSAGE:\n"+message+"\n\nREPOSITORY CONTEXT:\n"+repositoryContext);
        Map<String,Object> format=obj(Map.of("revisedRequirement",str(),"response",str(),"changes",arr(str()),"openQuestions",arr(str())));
        body.put("text", Map.of("format", Map.of("type","json_schema","name","requirement_refinement","strict",true,"schema",format)));
        HttpRequest req=HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses")).timeout(Duration.ofSeconds(120)).header("Authorization","Bearer "+apiKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body))).build();
        HttpResponse<String> res=client.send(req,HttpResponse.BodyHandlers.ofString());
        if(res.statusCode()<200||res.statusCode()>=300) throw new IllegalStateException("OpenAI refinement request failed (HTTP "+res.statusCode()+")");
        String text=extractOutputText(Json.object(Json.parse(res.body()))); return Json.object(Json.parse(text));
    }

    private Map<String,Object> call(String ticket, String repositoryContext, boolean generate) throws Exception {
        if (!configured()) throw new IllegalStateException("OPENAI_API_KEY is not configured on the Ticket2Test server.");
        Map<String,Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("store", false);
        body.put("instructions", SYSTEM + (generate ? GENERATE_MODE : ANALYSE_MODE));
        body.put("input", "BUSINESS REQUIREMENT:\n" + ticket + "\n\nREPOSITORY CONTEXT:\n" + repositoryContext);
        body.put("text", Map.of("format", schema()));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/responses"))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.stringify(body))).build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() < 200 || res.statusCode() >= 300) {
            Map<String,Object> err = Json.object(Json.parse(res.body()));
            String message = Json.string(Json.object(err.get("error")).get("message"));
            throw new IllegalStateException("OpenAI request failed" + (message.isBlank() ? " (HTTP " + res.statusCode() + ")" : ": " + message));
        }
        Map<String,Object> root = Json.object(Json.parse(res.body()));
        String outputText = extractOutputText(root);
        if (outputText.isBlank()) throw new IllegalStateException("OpenAI returned no structured agent output.");
        return Json.object(Json.parse(outputText));
    }

    private static String extractOutputText(Map<String,Object> root) {
        for (Object item : Json.array(root.get("output"))) {
            Map<String,Object> m = Json.object(item);
            for (Object content : Json.array(m.get("content"))) {
                Map<String,Object> c = Json.object(content);
                if ("output_text".equals(Json.string(c.get("type")))) return Json.string(c.get("text"));
            }
        }
        return Json.string(root.get("output_text"));
    }

    private static Map<String,Object> schema() {
        Map<String,Object> behaviour = obj(Map.of(
                "id", str(), "description", str(), "level", enumS("UNIT","INTEGRATION","API","ACCEPTANCE"),
                "explicit", bool(), "ambiguity", str()));
        Map<String,Object> mapping = obj(Map.of(
                "behaviourId", str(), "className", str(), "methodName", str(), "source", enumS("EXISTING","GENERATED")));
        Map<String,Object> generated = obj(Map.of(
                "path", str(), "className", str(), "code", str(), "covers", arr(str())));
        Map<String,Object> root = obj(Map.of(
                "title", str(), "summary", str(), "testable", bool(), "questions", arr(str()),
                "behaviours", arr(behaviour), "evidenceMappings", arr(mapping), "generatedTests", arr(generated)));
        return Map.of("type","json_schema","name","ticket2test_plan","strict",true,"schema",root);
    }
    private static Map<String,Object> str(){ return Map.of("type","string"); }
    private static Map<String,Object> bool(){ return Map.of("type","boolean"); }
    private static Map<String,Object> enumS(String... v){ return Map.of("type","string","enum",List.of(v)); }
    private static Map<String,Object> arr(Map<String,Object> item){ return Map.of("type","array","items",item); }
    private static Map<String,Object> obj(Map<String,Object> props){ return Map.of("type","object","properties",props,"required",new ArrayList<>(props.keySet()),"additionalProperties",false); }

    private static final String SYSTEM = """
You are the official Ticket2Test AI Quality Engineering Agent. Your job is to determine whether a software repository contains executable evidence that proves the submitted business requirement.

Rules:
- Never invent business requirements. Separate explicit requirements from reasonable inferred behaviours.
- Surface ambiguity in the ambiguity field and clarification questions rather than silently resolving it.
- Inspect the supplied Java/Maven/JUnit repository context before deciding coverage.
- Map existing tests to behaviours only when the test code meaningfully asserts that behaviour.
- Use exact existing class and method names from the repository.
- Do not fabricate execution results or coverage percentages; Ticket2Test measures those after your plan.
- Prefer deterministic unit tests. Use integration/API/acceptance only when the requirement truly needs them.
- title should be a concise professional requirement title. summary should explain the verification intent in one sentence.
""";

    private static final String ANALYSE_MODE = """

MODE: VERIFY EXISTING EVIDENCE ONLY.
- Do not generate tests in this pass. generatedTests MUST be an empty array.
- evidenceMappings MUST contain only EXISTING tests that genuinely prove a required behaviour.
- This pass is the baseline verification state before Ticket2Test creates any missing verification.
""";

    private static final String GENERATE_MODE = """

MODE: GENERATE MISSING VERIFICATION.
- First map all adequate existing tests.
- Generate repository-aware JUnit tests ONLY for behaviours that lack adequate executable evidence and are sufficiently unambiguous to test safely.
- Generated tests must compile against the submitted repository as-is. Do not modify production code.
- Each generated test must use a path under src/test/java and include its package declaration when required.
- evidenceMappings must include both adequate EXISTING mappings and GENERATED mappings for every generated test method.
- For generated mappings, use the exact generated class and method names.
""";
}
