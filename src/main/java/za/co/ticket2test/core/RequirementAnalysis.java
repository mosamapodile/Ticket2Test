package za.co.ticket2test.core;

import java.util.List;

public record RequirementAnalysis(
        String ticket,
        boolean testable,
        List<String> questions,
        List<Behaviour> behaviours
) {
}
