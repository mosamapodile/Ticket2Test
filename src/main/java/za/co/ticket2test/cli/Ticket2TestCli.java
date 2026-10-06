package za.co.ticket2test.cli;

import za.co.ticket2test.core.Behaviour;
import za.co.ticket2test.core.RequirementAnalysis;
import za.co.ticket2test.core.RuleBasedTicketAnalyzer;
import za.co.ticket2test.core.TicketAnalyzer;

public final class Ticket2TestCli {

    private static final String DEFAULT_TICKET =
            "Lock a user account for 30 minutes after five consecutive failed login attempts.";

    private Ticket2TestCli() {
    }

    public static void main(String[] args) {
        String ticket = args.length == 0 ? DEFAULT_TICKET : String.join(" ", args);
        TicketAnalyzer analyzer = new RuleBasedTicketAnalyzer();
        RequirementAnalysis analysis = analyzer.analyze(ticket);

        System.out.println("\nTicket2Test AI");
        System.out.println("Because tested code isn't always tested behaviour.\n");
        System.out.println("TICKET");
        System.out.println(ticket);
        System.out.println();

        if (!analysis.testable()) {
            System.out.println("REQUIREMENT HEALTH: NOT READY");
            analysis.questions().forEach(q -> System.out.println("- " + q));
            return;
        }

        System.out.println("REQUIREMENT HEALTH: TESTABLE WITH CLARIFICATION");
        analysis.questions().forEach(q -> System.out.println("QUESTION: " + q));
        System.out.println("\nOBSERVABLE BEHAVIOURS");
        for (Behaviour behaviour : analysis.behaviours()) {
            System.out.printf("%s [%s] %s%n",
                    behaviour.id(), behaviour.level(), behaviour.description());
        }

        System.out.println("\nNEXT: inspect repository -> map existing tests -> find gaps -> generate missing tests -> run -> prove");
    }
}
