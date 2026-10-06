package com.terraformers.modernization.reference.opensearch;

import static org.assertj.core.api.Assertions.assertThat;

import com.terraformers.modernization.reference.ReferenceDocument;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceEvidenceSelectorTest {
    private final ReferenceEvidenceSelector selector = new ReferenceEvidenceSelector();

    @Test
    void expandsOnlyWhenCoverageCannotBePreservedWithinBaseBudget() {
        List<String> resources = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> "aws_service_" + index)
                .toList();
        List<ReferenceDocument> global = java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(index -> document(
                        "global-" + index, "PROVIDER_SCHEMA", 1, 1, resources.get(index - 1)))
                .toList();
        List<ReferenceEvidenceSelector.Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < global.size(); index++) {
            candidates.add(candidate(global.get(index), true, true, index));
        }
        for (int index = 8; index < resources.size(); index++) {
            candidates.add(candidate(
                    document("target-" + (index + 1), "PROVIDER_SCHEMA", 1, 1, resources.get(index)),
                    true,
                    false,
                    candidates.size()));
        }

        List<ReferenceDocument> selected = selector.select(candidates, global, resources, 8, 16);

        assertThat(selected).hasSize(12);
        assertThat(selected.stream().flatMap(document -> document.resourceTypes().stream()).toList())
                .containsAll(resources);
    }

    @Test
    void hardMaxStopsCoverageGrowthAndDecisionDoesNotExpandPostCoverageBudget() {
        List<String> resources = java.util.stream.IntStream.rangeClosed(1, 12)
                .mapToObj(index -> "aws_service_" + index)
                .toList();
        List<ReferenceDocument> global = java.util.stream.IntStream.rangeClosed(1, 8)
                .mapToObj(index -> document(
                        "global-" + index, "PROVIDER_SCHEMA", 1, 1, resources.get(index - 1)))
                .toList();
        List<ReferenceEvidenceSelector.Candidate> candidates = new ArrayList<>();
        for (int index = 0; index < global.size(); index++) {
            candidates.add(candidate(global.get(index), true, true, index));
        }
        for (int index = 8; index < resources.size(); index++) {
            candidates.add(candidate(
                    document("target-" + (index + 1), "PROVIDER_SCHEMA", 1, 1, resources.get(index)),
                    true,
                    false,
                    candidates.size()));
        }
        candidates.add(candidate(
                document("decision", "PROJECT_DECISION", 1, 100, resources.get(0)),
                false,
                false,
                candidates.size()));

        List<ReferenceDocument> selected = selector.select(candidates, global, resources, 8, 10);

        assertThat(selected).hasSize(10);
        assertThat(selected.stream().flatMap(document -> document.resourceTypes().stream()).toList())
                .contains(resources.subList(0, 10).toArray(String[]::new))
                .doesNotContain(resources.get(10), resources.get(11));
    }

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
        ReferenceDocument second = document("decision-two", "PROJECT_DECISION", 0.99, 100, "aws_alpha");
        List<ReferenceEvidenceSelector.Candidate> candidates = List.of(
                candidate(first, true, true, 0), candidate(second, false, false, 1));

        List<ReferenceDocument> firstRun = select(candidates, List.of(first), List.of("aws_alpha"), 1);
        List<ReferenceDocument> secondRun = select(candidates, List.of(first), List.of("aws_alpha"), 1);

        assertThat(firstRun).extracting(ReferenceDocument::id).containsExactly("decision-one");
        assertThat(secondRun).isEqualTo(firstRun);
        assertThat(firstRun).hasSize(1);
    }

    @Test
    void replacesBroadSelectedDecisionWithStructurallyCoherentDecisionWithoutLosingCoverage() {
        ReferenceDocument providerAlpha = document("provider-alpha", "PROVIDER_SCHEMA", 1, 1, "aws_alpha");
        ReferenceDocument providerBeta = document("provider-beta", "PROVIDER_DOCUMENTATION", 1, 1, "aws_beta");
        ReferenceDocument broad = document(
                "broad-decision", "PROJECT_DECISION", 0.9, 90, "aws_alpha", "aws_gamma");
        ReferenceDocument coherent = document(
                "coherent-decision", "PROJECT_DECISION", 0.5, 10, "aws_alpha");
        List<ReferenceEvidenceSelector.Candidate> candidates = List.of(
                candidate(providerAlpha, true, true, 0),
                candidate(providerBeta, true, true, 1),
                candidate(broad, true, true, 2),
                candidate(coherent, false, false, 3));

        List<ReferenceDocument> firstRun = select(
                candidates, List.of(providerAlpha, providerBeta, broad), List.of("aws_alpha", "aws_beta"), 3);
        List<ReferenceDocument> secondRun = select(
                candidates, List.of(providerAlpha, providerBeta, broad), List.of("aws_alpha", "aws_beta"), 3);

        assertThat(firstRun).extracting(ReferenceDocument::id)
                .containsExactly("provider-alpha", "provider-beta", "coherent-decision");
        assertThat(firstRun).extracting(ReferenceDocument::resourceTypes)
                .anySatisfy(resources -> assertThat(resources).contains("aws_alpha"))
                .anySatisfy(resources -> assertThat(resources).contains("aws_beta"));
        assertThat(firstRun).extracting(ReferenceDocument::authority)
                .contains("PROVIDER_SCHEMA", "PROVIDER_DOCUMENTATION");
        assertThat(secondRun).isEqualTo(firstRun);
        assertThat(firstRun).hasSize(3);
    }

    @Test
    void replacesBroaderPartiallyUnsupportedDecisionWithSelfContainedDecisionAtSaturatedLimit() {
        ReferenceDocument providerAlpha = document("provider-alpha", "PROVIDER_SCHEMA", 1, 1, "aws_alpha");
        ReferenceDocument providerBeta = document("provider-beta", "PROVIDER_SCHEMA", 1, 1, "aws_beta");
        ReferenceDocument providerGamma = document("provider-gamma", "PROVIDER_SCHEMA", 1, 1, "aws_gamma");
        ReferenceDocument providerDelta = document("provider-delta", "PROVIDER_SCHEMA", 1, 1, "aws_delta");
        ReferenceDocument broad = document(
                "broad-decision", "PROJECT_DECISION", 0.99, 100,
                "aws_alpha", "aws_beta", "aws_gamma", "aws_external");
        ReferenceDocument coherent = document(
                "coherent-decision", "PROJECT_DECISION", 0.1, 1, "aws_delta");
        List<ReferenceEvidenceSelector.Candidate> candidates = List.of(
                candidate(providerAlpha, true, true, 0),
                candidate(providerBeta, true, true, 1),
                candidate(providerGamma, true, true, 2),
                candidate(providerDelta, true, true, 3),
                candidate(broad, true, true, 4),
                candidate(coherent, false, false, 5));
        List<ReferenceDocument> selected =
                List.of(providerAlpha, providerBeta, providerGamma, providerDelta, broad);
        List<String> resources = List.of("aws_alpha", "aws_beta", "aws_gamma", "aws_delta");

        List<ReferenceDocument> firstRun = select(candidates, selected, resources, 5);
        List<ReferenceDocument> secondRun = select(candidates, selected, resources, 5);

        assertThat(firstRun).extracting(ReferenceDocument::id).containsExactly(
                "provider-alpha", "provider-beta", "provider-gamma", "provider-delta", "coherent-decision");
        assertThat(firstRun).extracting(ReferenceDocument::resourceTypes)
                .anySatisfy(types -> assertThat(types).contains("aws_alpha"))
                .anySatisfy(types -> assertThat(types).contains("aws_beta"))
                .anySatisfy(types -> assertThat(types).contains("aws_gamma"))
                .anySatisfy(types -> assertThat(types).contains("aws_delta"));
        assertThat(firstRun).extracting(ReferenceDocument::authority)
                .containsOnly("PROVIDER_SCHEMA", "PROJECT_DECISION");
        assertThat(secondRun).isEqualTo(firstRun);
        assertThat(firstRun).hasSize(5);
    }

    @Test
    void prefersGreaterMatchedCountWhenUnsupportedCountsAreEqual() {
        ReferenceDocument base = document("base", "PROVIDER_SCHEMA", 1, 1, "aws_alpha", "aws_beta");
        ReferenceDocument narrow = document("narrow", "PROJECT_DECISION", 0.99, 100, "aws_alpha");
        ReferenceDocument wider = document("wider", "PROJECT_DECISION", 0.1, 1, "aws_alpha", "aws_beta");

        List<ReferenceDocument> result = select(
                List.of(candidate(base, true, true, 0), candidate(narrow, true, false, 1),
                        candidate(wider, true, false, 2)),
                List.of(base), List.of("aws_alpha", "aws_beta"), 2);

        assertThat(result).extracting(ReferenceDocument::id).containsExactly("base", "wider");
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

    @Test
    void officialDocumentReplacesSchemaOnlyCoverageWithoutIncreasingBudget() {
        var schema = document("schema", "PROVIDER_SCHEMA", 1, 100, "aws_alpha");
        var official = official("official", "aws_alpha");
        var selected = selector.select(List.of(candidate(schema, true, true, 0),
                candidate(official, true, false, 1)), List.of(schema), List.of("aws_alpha"), 1, 1);
        assertThat(selected).containsExactly(official);
    }

    @Test
    void unsupportedDocumentTypeCannotBlockSupportedOfficialSelection() {
        var invalidType = document("generic", "PROVIDER_DOCUMENTATION", 1, 100, "aws_alpha");
        var example = new ReferenceDocument("example", "example", "content", 1,
                "AWS_PROVIDER_EXAMPLE", List.of("aws_alpha"), "", "", "",
                "PROVIDER_DOCUMENTATION", 1, List.of());
        var selected = selector.select(List.of(candidate(invalidType, true, true, 0),
                candidate(example, true, false, 1)), List.of(invalidType), List.of("aws_alpha"), 1, 1);
        assertThat(selected).containsExactly(example);
    }

    @Test
    void decisionPromotionCannotEvictSoleOfficialDocumentation() {
        var official = official("official", "aws_alpha");
        var schema = document("schema", "PROVIDER_SCHEMA", 1, 100, "aws_alpha");
        var decision = document("decision", "PROJECT_DECISION", 1, 100, "aws_alpha");
        var selected = selector.select(List.of(candidate(official, true, true, 0),
                candidate(schema, true, true, 1), candidate(decision, false, false, 2)),
                List.of(official, schema), List.of("aws_alpha"), 2, 2);
        assertThat(selected).containsExactly(official, decision);
    }

    @Test
    void closureMergePreservesDecisionsAndIdentityWhileGrowingOnlyToHardLimit() {
        var alpha = official("alpha", "aws_alpha");
        var beta = official("beta", "aws_beta");
        var decision = document("decision", "PROJECT_DECISION", 1, 1, "aws_alpha");
        var initial = List.of(alpha, decision, alpha);
        var result = selector.merge(initial, List.of(alpha, beta), List.of("aws_alpha", "aws_beta"), 3);
        assertThat(result).containsExactly(alpha, decision, beta);
        assertThat(selector.merge(initial, List.of(alpha, beta), List.of("aws_alpha", "aws_beta"), 3))
                .isEqualTo(result);
        assertThat(selector.merge(List.of(alpha, decision), List.of(beta), List.of("aws_alpha", "aws_beta"), 2))
                .containsExactly(alpha, decision);
    }

    @Test
    void retainsInitialGenericReferencesWhenClosureFitsBudget() {
        var schema = document("schema", "PROVIDER_SCHEMA", 1, 1, "aws_alpha");
        var documentation = official("official", "aws_alpha");
        assertThat(selector.merge(List.of(schema), List.of(documentation), List.of("aws_alpha"), 2))
                .containsExactly(schema, documentation);
    }

    private ReferenceDocument official(String id, String resource) {
        return new ReferenceDocument(id, id, "official content", 1, "AWS_PROVIDER_DOC", List.of(resource),
                "", "5.100.0", "any-corpus", "PROVIDER_DOCUMENTATION", 1, List.of());
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
