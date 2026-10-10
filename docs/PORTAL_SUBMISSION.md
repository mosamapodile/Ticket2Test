# Portal Submission Copy

## Project name

**Ticket2Test AI**

## One-line description

An AI Quality Engineering Agent that turns business tickets into traceable, executable tests and proves which required behaviours are covered, missing or failing.

## Problem

A green test suite does not prove that every behaviour requested in a business ticket was actually verified. Requirements, implementation and tests can drift apart, leaving teams with false confidence before release.

## Solution

Ticket2Test reads a ticket, identifies its observable behaviours, inspects the repository and existing tests, detects behavioural gaps, generates only the missing tests, executes them with the real test framework, and maps the result back to the originating requirement.

## Differentiator

Ticket2Test is not just an AI test generator. Its core asset is requirement-to-verification traceability:

`Ticket -> Behaviour -> Existing Test -> Missing Test -> Generated Test -> Execution Result`

## Target user

Small and fast-moving software teams where developers own much of the testing and dedicated QA capacity is limited.

## MVP scope

- One business ticket.
- One Java repository.
- Maven + JUnit 5.
- One orchestrated agent.
- Requirement health + behaviour extraction.
- Existing-test inspection.
- Gap detection.
- Missing-test generation.
- Real test execution.
- Ticket-level PASS / FAIL / NOT FULLY VERIFIED result.

## Demo sentence

Ticket2Test takes a login-lockout ticket and a repository with passing tests, discovers that the suite never proves the 30-minute unlock requirement, generates the missing JUnit test and exposes the seeded defect.

## Business value

- Less manual test-design work.
- Earlier discovery of requirement ambiguity and behavioural gaps.
- Reduced rework and regression risk.
- Stronger release confidence at ticket level.

## Suggested tags

AI, Software Testing, Quality Engineering, Developer Tools, Java, JUnit, Maven, Agentic AI, Requirements Engineering
