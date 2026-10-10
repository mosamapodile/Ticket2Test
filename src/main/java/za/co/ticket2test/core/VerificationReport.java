package za.co.ticket2test.core;

import java.util.List;

public record VerificationReport(
        String ticket,
        VerificationStatus status,
        List<BehaviourEvidence> evidence
) {
}
