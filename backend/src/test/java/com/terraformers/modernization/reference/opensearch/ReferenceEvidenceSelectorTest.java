package com.terraformers.modernization.reference.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.terraformers.modernization.reference.ReferenceDocument;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceEvidenceSelectorTest {
    private final ReferenceEvidenceSelector selector = new ReferenceEvidenceSelector();

    @Test
    void admitsSelfContainedDecisionEvenWhenGlobalAlreadyCoversEveryResource() {
        ReferenceDocument alpha = document("alpha", "PROVIDER_SCHEMA", 1, 1, "aws_alpha");
        ReferenceDocument beta = document("beta", "PROVIDER_SCHEMA", 1, 1, "aws_beta");
        ReferenceDocument redundant = document("redundant", "PROVIDER_DOCUMENTATION", 1, 1, "aws_alpha");
        ReferenceDocument decision = document("decision", "PROJECT_DECISION", 0.8, 2, "aws_alpha");

        List<ReferenceDocument> result = select(
                List.of(candidate(alpha, true, true, 0), candidate(beta, true, true, 1),
                        candidate(redundant, true, true, 2), candidate(decision, false, false, 3)),
                List.of(alpha, beta, redundant), List.of("aws_alpha", "aws_beta"), 3);

        assertThat(result).extracting(ReferenceDocument::id).containsExactly("alpha", "beta", "decision");
    }

    @Test
    void protectsOnlyProviderAnchorAndRequestedResourceCoverage() {
        ReferenceDocument providerAlpha = document("provider-alpha", "PROVIDER_SCHEMA", 1, 1, "aws_alpha");
        ReferenceDocument beta = document("beta", "", 1, 1, "aws_beta");
        ReferenceDocument decision = document("decision", "PROJECT_DECISION", 1, 1, "aws_alpha");

        List<ReferenceDocument> result = select(
                List.of(candidate(providerAlpha, true, true, 0), candidate(beta, true, true, 1),
                        candidate(decision, false, false, 2)),
                List.of(providerAlpha, beta), List.of("aws_alpha", "aws_beta"), 2);

        assertThat(result).extracting(ReferenceDocument::id).containsExactly("provider-alpha", "beta");
    }

    @Test
    void requiresStructuralCoherenceOrNormalSemanticCorroboration() {
        ReferenceDocument base = document("base", "PROVIDER_SCHEMA", 1, 1, "aws_alpha");
        ReferenceDocument broad = document(
                "broad", "PROJECT_DECISION", 1, 100, "aws_alpha", "aws_gamma", "aws_delta");
        assertThat(select(
                List.of(candidate(base, true, true, 0), candidate(broad, false, false, 1)),
                List.of(base), List.of("aws_alpha"), 2))
                .extracting(ReferenceDocument::id).containsExactly("base");

        assertThat(select(
                List.of(candidate(base, true, true, 0), candidate(broad, true, false, 1)),
                List.of(base), List.of("aws_alpha"), 2))
                .extracting(ReferenceDocument::id).containsExactly("base", "broad");
    }

    @Test
    void ordersDecisionsByCoherenceBeforeScoreAndPriority() {
        ReferenceDocument base = document("base", "PROVIDER_SCHEMA", 1, 1, "aws_alpha");
        ReferenceDocument broad = document("broad", "PROJECT_DECISION", 0.99, 100, "aws_alpha", "aws_gamma");
        ReferenceDocument coherent = document("coherent", "PROJECT_DECISION", 0.2, 1, "aws_alpha");

        List<ReferenceDocument> result = select(
                List.of(candidate(base, true, true, 0), candidate(broad, true, false, 1),
                        candidate(coherent, false, false, 2)),
                List.of(base), List.of("aws_alpha"), 2);

        assertThat(result).extracting(ReferenceDocument::id).containsExactly("base", "coherent");
    }

    @Test
    void keepsDistinctSameResourceDecisionsAndDeduplicatesDocumentIdentity() {
        ReferenceDocument first = document("decision-one", "PROJECT_DECISION", 1, 1, "aws_alpha", "aws_beta");
        ReferenceDocument second = document("decision-two", "PROJECT_DECISION", 0.9, 1, "aws_alpha");

        List<ReferenceDocument> result = select(
                List.of(candidate(first, true, true, 0), candidate(first, false, false, 0),
                        candidate(second, false, false, 1)),
                List.of(first), List.of("aws_alpha", "aws_beta"), 2);

        assertThat(result).extracting(ReferenceDocument::id)
                .containsExactly("decision-one", "decision-two").doesNotHaveDuplicates();
    }

    @Test
    void doesNotReplaceSelectedDecisionAtSaturatedLimitAndIsDeterministic() {
        ReferenceDocument first = document("decision-one", "PROJECT_DECISION", 0.8, 1, "aws_alpha");
        ReferenceDocument second = document("decision-two", "PROJECT_DECISION", 0.7, 1, "aws_alpha");
        List<ReferenceEvidenceSelector.Candidate> candidates = List.of(
                candidate(first, true, true, 0), candidate(second, false, false, 1));

        List<ReferenceDocument> firstRun = select(candidates, List.of(first), List.of("aws_alpha"), 1);
        List<ReferenceDocument> secondRun = select(candidates, List.of(first), List.of("aws_alpha"), 1);

        assertThat(firstRun).extracting(ReferenceDocument::id).containsExactly("decision-one");
        assertThat(secondRun).isEqualTo(firstRun);
        assertThat(firstRun).hasSize(1);
    }

    @Test
    void retainsA4SupplementationWhenThereIsNoEligibleDecision() {
        ReferenceDocument alpha = document("alpha", "", 1, 1, "aws_alpha");
        ReferenceDocument unrelated = document("unrelated", "", 1, 1, "aws_gamma");
        ReferenceDocument beta = document("beta", "", 1, 1, "aws_beta");

        List<ReferenceDocument> result = select(
                List.of(candidate(alpha, true, true, 0), candidate(unrelated, true, true, 1),
                        candidate(beta, true, false, 2)),
                List.of(alpha, unrelated), List.of("aws_alpha", "aws_beta"), 2);

        assertThat(result).extracting(ReferenceDocument::id).containsExactly("alpha", "beta");
    }

    private List<ReferenceDocument> select(
            List<ReferenceEvidenceSelector.Candidate> candidates,
            List<ReferenceDocument> global,
            List<String> resources,
            int limit
    ) {
        return selector.select(new ArrayList<>(candidates), global, resources, limit);
    }

    private ReferenceEvidenceSelector.Candidate candidate(
            ReferenceDocument document, boolean normal, boolean global, int order) {
        return new ReferenceEvidenceSelector.Candidate(document, normal, global, order);
    }

    private ReferenceDocument document(
            String id, String authority, double score, int priority, String... resources) {
        return new ReferenceDocument(id, id, "content", score, "", List.of(resources), "", "", "",
                authority, priority, List.of());
    }
}
