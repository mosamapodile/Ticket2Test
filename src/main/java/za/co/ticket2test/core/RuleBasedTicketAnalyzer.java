package za.co.ticket2test.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic vertical-slice analyser for the hackathon demo.
 *
 * The production version should place an LLM-backed analyser behind
 * TicketAnalyzer while preserving the same structured output contract.
 */
public final class RuleBasedTicketAnalyzer implements TicketAnalyzer {

    @Override
    public RequirementAnalysis analyze(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return new RequirementAnalysis(
                    "",
                    false,
                    List.of("Provide a non-empty business ticket."),
                    List.of()
            );
        }

        String normalized = ticket.toLowerCase(Locale.ROOT);
        boolean loginLockout = normalized.contains("lock")
                && normalized.contains("failed login")
                && normalized.contains("30 minute")
                && normalized.contains("five");

        if (!loginLockout) {
            return new RequirementAnalysis(
                    ticket,
                    false,
                    List.of("The current MVP only supports the prepared login-lockout vertical slice."),
                    List.of()
            );
        }

        List<String> questions = new ArrayList<>();
        questions.add("Does a successful login reset the consecutive-failure counter?");

        List<Behaviour> behaviours = List.of(
                new Behaviour("B1", "Failed attempts 1-4 allow another login attempt.", VerificationLevel.UNIT),
                new Behaviour("B2", "The fifth consecutive failed login locks the account.", VerificationLevel.UNIT),
                new Behaviour("B3", "A login attempt while the account is locked is rejected.", VerificationLevel.UNIT),
                new Behaviour("B4", "The account becomes available again after 30 minutes.", VerificationLevel.UNIT)
        );

        return new RequirementAnalysis(ticket, true, questions, behaviours);
    }
}
