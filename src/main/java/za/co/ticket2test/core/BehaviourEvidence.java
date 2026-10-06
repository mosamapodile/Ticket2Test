package za.co.ticket2test.core;

public record BehaviourEvidence(
        Behaviour behaviour,
        boolean covered,
        boolean passing,
        String evidence
) {
}
