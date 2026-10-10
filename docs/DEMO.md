# Two-Minute Demo Script

## Setup

Business ticket:

> Lock a user account for 30 minutes after five consecutive failed login attempts.

Prepared repository:

a connected or uploaded test repository (for example Robot Worlds)

It already contains tests that pass for several obvious behaviours, while the production implementation contains a deliberately seeded unlock defect.

## Story

### 0:00-0:20 — Hook

"This repository has passing tests. But do those tests prove the ticket?"

Paste the ticket and load the sample repo.

### 0:20-0:45 — Requirement model

Show Ticket2Test extracting:

- attempts 1-4 allow retry;
- fifth failed attempt locks;
- locked login is rejected;
- unlock occurs after 30 minutes.

Show the clarification question around successful-login reset behaviour.

### 0:45-1:10 — Gap

Ticket2Test scans the existing tests and marks some behaviours covered while identifying the missing 30-minute unlock proof.

### 1:10-1:35 — Generate

Click **Generate Missing Tests**.

Show generated repository-aware JUnit code.

### 1:35-1:50 — Execute

Run Maven tests. The newly generated unlock test fails against the deliberately flawed implementation.

### 1:50-2:00 — Prove

Final screen:

```text
TEST SUITE GREEN BEFORE ANALYSIS
TICKET NOT FULLY VERIFIED
B4 FAIL — account does not unlock after 30 minutes
```

Close with:

**Ticket2Test — Because tested code isn't always tested behaviour.**
