package com.terraformers.modernization.reference.opensearch;

import com.terraformers.modernization.reference.ReferenceDocument;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ReferenceEvidenceSelector {

    private static final Set<String> PROVIDER_AUTHORITIES = Set.of(
            "PROVIDER_SCHEMA", "PROVIDER_DOCUMENTATION");
    private static final String PROJECT_DECISION = "PROJECT_DECISION";

    List<ReferenceDocument> select(
            Collection<Candidate> candidates,
            List<ReferenceDocument> global,
            List<String> resourceTypes,
            int limit
    ) {
        List<ReferenceDocument> selected = new ArrayList<>(global.stream().limit(limit).toList());
        supplementResourceCoverage(candidates, selected, resourceTypes, limit);
        promoteDecisions(candidates, selected, resourceTypes, limit);
        return List.copyOf(selected);
    }

    private void supplementResourceCoverage(
            Collection<Candidate> candidates,
            List<ReferenceDocument> selected,
            List<String> resourceTypes,
            int limit
    ) {
        Set<String> missing = new LinkedHashSet<>(resourceTypes);
        selected.forEach(document -> missing.removeAll(document.resourceTypes()));
        Set<String> selectedIds = ids(selected);
        List<Candidate> targeted = candidates.stream()
                .filter(Candidate::normalSemantic)
                .filter(candidate -> !candidate.globalSemantic())
                .toList();
        while (!missing.isEmpty()) {
            Set<String> currentMissing = Set.copyOf(missing);
            Candidate best = targeted.stream()
                    .filter(candidate -> !selectedIds.contains(candidate.document().id()))
                    .filter(candidate -> coverage(candidate.document(), currentMissing) > 0)
                    .min(Comparator
                            .<Candidate>comparingInt(candidate -> coverage(candidate.document(), currentMissing))
                            .reversed()
                            .thenComparing(Comparator.comparingInt(
                                    (Candidate candidate) -> candidate.document().priority()).reversed())
                            .thenComparingInt(Candidate::discoveryOrder))
                    .orElse(null);
            if (best == null) {
                return;
            }
            if (selected.size() < limit) {
                selected.add(best.document());
            } else {
                int replacement = safeResourceReplacement(selected, best.document(), resourceTypes);
                if (replacement < 0) {
                    return;
                }
                selectedIds.remove(selected.get(replacement).id());
                selected.set(replacement, best.document());
            }
            selectedIds.add(best.document().id());
            missing.removeAll(best.document().resourceTypes());
        }
    }

    private void promoteDecisions(
            Collection<Candidate> candidates,
            List<ReferenceDocument> selected,
            List<String> resourceTypes,
            int limit
    ) {
        Set<String> requested = new LinkedHashSet<>(resourceTypes);
        List<Candidate> eligible = candidates.stream()
                .filter(candidate -> PROJECT_DECISION.equals(candidate.document().authority()))
                .filter(candidate -> coverage(candidate.document(), requested) > 0)
                .filter(candidate -> unsupported(candidate.document(), requested) == 0
                        || candidate.normalSemantic())
                .sorted(decisionComparator(requested))
                .toList();
        Set<String> selectedIds = ids(selected);
        for (Candidate candidate : eligible) {
            if (!selectedIds.add(candidate.document().id())) {
                continue;
            }
            if (selected.size() < limit) {
                selected.add(candidate.document());
                continue;
            }
            int replacement = safeDecisionReplacement(selected, candidate.document(), resourceTypes);
            if (replacement < 0) {
                selectedIds.remove(candidate.document().id());
                continue;
            }
            selectedIds.remove(selected.get(replacement).id());
            selected.set(replacement, candidate.document());
        }
    }

    private Comparator<Candidate> decisionComparator(Set<String> requested) {
        return Comparator
                .<Candidate>comparingInt(candidate -> unsupported(candidate.document(), requested))
                .thenComparing(Comparator.comparingInt(
                        (Candidate candidate) -> coverage(candidate.document(), requested)).reversed())
                .thenComparing(Comparator.comparingDouble(
                        (Candidate candidate) -> candidate.document().score()).reversed())
                .thenComparing(Comparator.comparingInt(
                        (Candidate candidate) -> candidate.document().priority()).reversed())
                .thenComparingInt(Candidate::discoveryOrder);
    }

    private int safeResourceReplacement(
            List<ReferenceDocument> selected,
            ReferenceDocument replacement,
            List<String> resourceTypes
    ) {
        Set<String> existingCoverage = coveredResourceTypes(selected, resourceTypes);
        for (int index = selected.size() - 1; index >= 0; index--) {
            List<ReferenceDocument> proposed = replacing(selected, index, replacement);
            if (coveredResourceTypes(proposed, resourceTypes).containsAll(existingCoverage)) {
                return index;
            }
        }
        return -1;
    }

    private int safeDecisionReplacement(
            List<ReferenceDocument> selected,
            ReferenceDocument replacement,
            List<String> resourceTypes
    ) {
        Set<String> existingResources = coveredResourceTypes(selected, resourceTypes);
        Set<String> existingProviders = providerCoverage(selected, resourceTypes);
        for (boolean redundantOnly : List.of(true, false)) {
            for (int index = selected.size() - 1; index >= 0; index--) {
                ReferenceDocument removed = selected.get(index);
                if (PROJECT_DECISION.equals(removed.authority())) {
                    continue;
                }
                List<ReferenceDocument> without = new ArrayList<>(selected);
                without.remove(index);
                boolean redundant = coveredResourceTypes(without, resourceTypes).containsAll(existingResources)
                        && providerCoverage(without, resourceTypes).containsAll(existingProviders);
                if (redundantOnly != redundant) {
                    continue;
                }
                if (preservesCoverage(
                        selected, index, replacement, resourceTypes, existingResources, existingProviders)) {
                    return index;
                }
            }
        }
        Set<String> requested = new LinkedHashSet<>(resourceTypes);
        for (int index = selected.size() - 1; index >= 0; index--) {
            ReferenceDocument removed = selected.get(index);
            if (PROJECT_DECISION.equals(removed.authority())
                    && structurallyBetter(replacement, removed, requested)
                    && preservesCoverage(
                            selected, index, replacement, resourceTypes, existingResources, existingProviders)) {
                return index;
            }
        }
        return -1;
    }

    private boolean structurallyBetter(
            ReferenceDocument replacement, ReferenceDocument existing, Set<String> requested) {
        int replacementUnsupported = unsupported(replacement, requested);
        int existingUnsupported = unsupported(existing, requested);
        int replacementMatches = coverage(replacement, requested);
        int existingMatches = coverage(existing, requested);
        return replacementUnsupported < existingUnsupported
                || (replacementUnsupported == existingUnsupported
                        && replacementMatches > existingMatches);
    }

    private boolean preservesCoverage(
            List<ReferenceDocument> selected,
            int index,
            ReferenceDocument replacement,
            List<String> resourceTypes,
            Set<String> existingResources,
            Set<String> existingProviders
    ) {
        List<ReferenceDocument> proposed = replacing(selected, index, replacement);
        return coveredResourceTypes(proposed, resourceTypes).containsAll(existingResources)
                && providerCoverage(proposed, resourceTypes).containsAll(existingProviders);
    }

    private List<ReferenceDocument> replacing(
            List<ReferenceDocument> selected, int index, ReferenceDocument replacement) {
        List<ReferenceDocument> proposed = new ArrayList<>(selected);
        proposed.set(index, replacement);
        return proposed;
    }

    private Set<String> providerCoverage(List<ReferenceDocument> documents, List<String> resourceTypes) {
        return coveredResourceTypes(documents.stream()
                .filter(document -> PROVIDER_AUTHORITIES.contains(document.authority()))
                .toList(), resourceTypes);
    }

    private Set<String> coveredResourceTypes(List<ReferenceDocument> documents, List<String> resourceTypes) {
        Set<String> requested = new LinkedHashSet<>(resourceTypes);
        Set<String> covered = new LinkedHashSet<>();
        for (ReferenceDocument document : documents) {
            document.resourceTypes().stream().filter(requested::contains).forEach(covered::add);
        }
        return covered;
    }

    private int coverage(ReferenceDocument document, Set<String> resources) {
        return (int) document.resourceTypes().stream().distinct().filter(resources::contains).count();
    }

    private int unsupported(ReferenceDocument document, Set<String> requested) {
        return (int) document.resourceTypes().stream().distinct().filter(resource -> !requested.contains(resource)).count();
    }

    private Set<String> ids(List<ReferenceDocument> documents) {
        Set<String> ids = new LinkedHashSet<>();
        documents.forEach(document -> ids.add(document.id()));
        return ids;
    }

    record Candidate(
            ReferenceDocument document,
            boolean normalSemantic,
            boolean globalSemantic,
            int discoveryOrder
    ) {
    }
}
