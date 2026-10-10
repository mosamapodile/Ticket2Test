package za.co.ticket2test.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedTicketAnalyzerTest {

    private final TicketAnalyzer analyzer = new RuleBasedTicketAnalyzer();

    @Test
    void extractsPreparedLoginLockoutBehaviours() {
        RequirementAnalysis result = analyzer.analyze(
                "Lock a user account for 30 minutes after five consecutive failed login attempts."
        );

        assertTrue(result.testable());
        assertEquals(4, result.behaviours().size());
        assertTrue(result.questions().stream()
                .anyMatch(q -> q.toLowerCase().contains("successful login")));
    }

    @Test
    void rejectsBlankTicket() {
        RequirementAnalysis result = analyzer.analyze("   ");

        assertFalse(result.testable());
        assertTrue(result.behaviours().isEmpty());
    }

    @Test
    void keepsMvpScopeNarrow() {
        RequirementAnalysis result = analyzer.analyze("Add dark mode to the dashboard.");

        assertFalse(result.testable());
        assertTrue(result.behaviours().isEmpty());
    }
}
