# Ticket2Test Architecture

## Objective

Determine whether a business ticket's required behaviour is adequately verified by the repository's executable tests.

## Bounded agent loop

```text
Ticket
  |
  v
Requirement Analyzer
  |----> ambiguity/questions
  v
Behaviour Model
  |
  v
Repository Inspector
  |
  +----> source inventory
  +----> existing test inventory
  v
Gap Mapper
  |
  +----> covered behaviour
  +----> missing behaviour
  v
JUnit Generator
  |
  v
Controlled Maven Runner
  |
  v
Traceability Report
```

## Core data model

```text
Ticket
  -> Behaviour[]
      -> verification level
      -> ExistingTest? / MissingTest?
      -> GeneratedTest?
      -> ExecutionResult
```

## MVP design choices

- **Single agent:** easier to reason about, demo and constrain.
- **Structured outputs:** the LLM should return typed requirement/behaviour data, not unbounded prose.
- **Human-visible assumptions:** ambiguity is surfaced rather than silently invented.
- **Real execution:** generated tests only become evidence after compilation and execution.
- **Tests only:** the MVP does not modify production code.
- **Controlled workspace:** do not execute arbitrary uploaded repositories directly on a host machine.

## Planned components

| Component | Responsibility |
|---|---|
| `TicketAnalyzer` | Convert ticket into structured behaviours + questions |
| `RepositoryInspector` | Discover Java source and JUnit tests |
| `CoverageMapper` | Link behaviours to existing test evidence |
| `MissingTestGenerator` | Produce repository-aware JUnit 5 tests |
| `MavenRunner` | Compile and run in an isolated workspace |
| `VerificationReport` | Map result back to each behaviour |

## AI boundary

The AI is responsible for interpretation, decomposition, semantic matching and test-code generation. The Java compiler and JUnit/Maven runner are responsible for executable evidence.
