# Ticket2Test

**AI Quality Engineering Agent**

> Because tested code isn't always tested behaviour.

Ticket2Test turns software requirements into executable verification. Give it a business requirement and a Java/Maven/JUnit repository; the agent interprets the required behaviours, inspects existing tests, identifies behavioural gaps, generates repository-aware missing tests, executes the real test suite, measures JaCoCo coverage, and produces a traceable requirement-level verdict.

## What Ticket2Test proves

Ticket2Test keeps two forms of coverage separate:

- **Behaviour coverage** — the percentage of required behaviours backed by passing executable evidence.
- **Code coverage** — line, branch, method and class coverage measured by JaCoCo.

The verification chain is:

`Requirement → Behaviour → Test → Execution → Evidence`

## Current supported stack

- Java 17+
- Maven
- JUnit
- JaCoCo
- OpenAI Responses API for requirement and repository-aware reasoning

## Run Ticket2Test

### 1. Configure the OpenAI API key

WSL / Linux:

```bash
export OPENAI_API_KEY="your_key_here"
```

Optional model override:

```bash
export T2T_OPENAI_MODEL="gpt-5.6-terra"
```

The API key is read only by the Java backend. Never put it in the browser code or commit it to Git.

### 2. Build

```bash
mvn clean package
```

### 3. Start the application

```bash
mvn exec:java -Dexec.mainClass="za.co.ticket2test.web.Ticket2TestWebApp"
```

Open:

`http://localhost:8080`

Use `T2T_PORT` to change the port.

## Verification flow

1. Paste a software requirement, user story, acceptance criteria or ticket.
2. Attach a Java/Maven/JUnit repository ZIP.
3. Ticket2Test builds a structured requirement model.
4. Existing source and tests are inspected.
5. Existing tests are mapped to required behaviours.
6. Missing verification is generated as repository-aware JUnit test code.
7. Generated tests are written only into the isolated temporary repository copy.
8. Maven executes the suite.
9. JaCoCo reports deterministic code coverage.
10. Ticket2Test returns behaviour-level evidence and the final requirement verdict.

Uploaded repositories are extracted into a temporary local workspace and deleted after the verification request completes. The application limits ZIP and extracted sizes and blocks ZIP path traversal.

## Environment variables

| Variable | Purpose | Default |
| --- | --- | --- |
| `OPENAI_API_KEY` | OpenAI API authentication | required |
| `T2T_OPENAI_MODEL` | Model used by the Ticket2Test agent | `gpt-5.6-terra` |
| `T2T_PORT` | Local web server port | `8080` |

## Product principle

Test generation is a core capability, but Ticket2Test does more than emit test code. A generated test only becomes evidence after it is compiled, executed and linked back to the originating required behaviour.

## Verification workflow

The web application uses two evidence passes:

1. **Verify requirement** analyses the submitted requirement and repository, maps only existing JUnit evidence, runs the current suite, and reports measured JaCoCo line/branch/method coverage plus behaviour coverage.
2. **Generate missing tests** asks the Ticket2Test Agent to create repository-aware JUnit tests only for safe missing behaviours, executes the expanded suite in an isolated copy, then recalculates all coverage and evidence from the actual run.

Coverage values in the UI are driven by the server response and animate to the measured result; they are not static display values or model-generated percentages.

## Coverage semantics

Ticket2Test keeps AI reasoning separate from measured evidence:

- **Behaviour coverage** = behaviours with at least one mapped test that actually executed and passed / total required behaviours.
- **Line / branch / method / class coverage** = aggregated JaCoCo XML counters from the executed Maven project (including supported multi-module projects).
- A mapped test that cannot be matched to Surefire execution is marked `UNEXECUTABLE`, not verified.
- If Maven cannot produce a JaCoCo report (for example, compilation fails), code coverage is reported as **N/A**, never fabricated as `0%`.
- Test assertion failures do not prevent JaCoCo reporting; Ticket2Test keeps the Maven lifecycle running long enough to collect coverage, while still reporting an effective failing execution result.

The **Generate missing tests** action appears after baseline verification whenever at least one required behaviour is not verified. Generated tests are created only for sufficiently unambiguous gaps, written into an isolated workspace, compiled, executed, and included in the recalculated evidence report.

## Engineering Workspace (integrated)

The original ZIP verification workflow remains available. Ticket2Test now also includes an additive engineering workspace:

- **Remote repository connection** — connect a public HTTPS Git repository and keep an isolated live workspace for the browser session.
- **Native in-app terminal** — run controlled repository commands (`git status`, `git branch`, `git log`, `git diff`, `pwd`, `ls`, `mvn test`, `./mvnw test`) directly inside T2T.
- **Requirements Copilot** — chat against the current requirement and repository context; explicit edits are applied back to the requirement editor while unresolved ambiguity stays visible.
- **Remote-to-verification bridge** — a connected repository is archived from the live workspace and passed through the same existing Ticket2Test verification/generation engine, so the original evidence semantics are unchanged.

### Security boundary

The embedded terminal is intentionally not an unrestricted host shell. Remote URLs must use public HTTPS without embedded credentials, workspaces live under temporary isolated directories, and only a small allowlist of read/test commands is executable. This keeps the product useful for a live demo without turning the web endpoint into arbitrary command execution.

### Product direction

The integrated workflow is now:

`Remote Repo → Requirement → Copilot Refinement → Behaviour Model → Test Evidence → Native Test Execution → Requirement Verdict`

This is the foundation for future authenticated GitHub/GitLab installation flows, branch/PR awareness, persistent cloud workspaces, streaming PTY terminals, ticket-system integrations, and organisation-level traceability.
