package com.terraformers.modernization.reference.opensearch;

import com.terraformers.modernization.reference.ReferenceDocument;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

public final class ReferenceEvidenceSelector {

    private static final Set<String> PROVIDER_AUTHORITIES = Set.of(
            "PROVIDER_SCHEMA", "PROVIDER_DOCUMENTATION");
    private static final String PROJECT_DECISION = "PROJECT_DECISION";

    List<ReferenceDocument> select(
            Collection<Candidate> candidates,
            List<ReferenceDocument> global,
            List<String> resourceTypes,
            int limit
    ) {
        return select(candidates, global, resourceTypes, limit, limit);
    }

    List<ReferenceDocument> select(
            Collection<Candidate> candidates,
            List<ReferenceDocument> global,
            List<String> resourceTypes,
            int baseLimit,
            int maxLimit
    ) {
        if (baseLimit <= 0 || maxLimit < baseLimit) {
            throw new IllegalArgumentException("evidence limits must be positive and max must be >= base");
        }
        List<ReferenceDocument> selected = new ArrayList<>(global.stream().limit(baseLimit).toList());
        supplementResourceCoverage(candidates, selected, resourceTypes, baseLimit, maxLimit, true);
        supplementResourceCoverage(candidates, selected, resourceTypes, baseLimit, maxLimit, false);
        int decisionLimit = Math.min(maxLimit, Math.max(baseLimit, selected.size()));
        promoteDecisions(candidates, selected, resourceTypes, decisionLimit);
        return List.copyOf(selected);
    }

    /** Reuse the bounded selector while retaining initial document identity and project decisions. */
    public List<ReferenceDocument> merge(List<ReferenceDocument> initial, List<ReferenceDocument> closure,
            List<String> resourceTypes, int maxLimit) {
        var candidates = new LinkedHashMap<String, Candidate>();
        initial.forEach(document -> candidates.putIfAbsent(document.id(),
                new Candidate(document, true, true, candidates.size())));
        List<ReferenceDocument> retained = candidates.values().stream().map(Candidate::document).toList();
        closure.forEach(document -> candidates.putIfAbsent(document.id(),
                new Candidate(document, true, false, candidates.size())));
        if (maxLimit <= 0) throw new IllegalArgumentException("evidence limit must be positive");
        if (candidates.size() <= maxLimit) {
            return candidates.values().stream().map(Candidate::document).toList();
        }
        int baseLimit = Math.min(maxLimit, Math.max(1, retained.size()));
        return select(candidates.values(), retained, resourceTypes, baseLimit, maxLimit);
    }

    private void supplementResourceCoverage(
            Collection<Candidate> candidates,
            List<ReferenceDocument> selected,
            List<String> resourceTypes,
            int baseLimit,
            int maxLimit,
            boolean officialOnly
    ) {
        Set<String> missing = new LinkedHashSet<>(resourceTypes);
        selected.stream().filter(document -> !officialOnly || document.isOfficialProviderDocumentation())
                .forEach(document -> missing.removeAll(document.resourceTypes()));
        Set<String> selectedIds = ids(selected);
        List<Candidate> targeted = candidates.stream()
                .filter(Candidate::normalSemantic)
                .filter(candidate -> officialOnly
                        ? candidate.document().isOfficialProviderDocumentation() : !candidate.globalSemantic())
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
            if (selected.size() < baseLimit) {
                selected.add(best.document());
            } else {
                int replacement = safeResourceReplacement(selected, best.document(), resourceTypes);
                if (replacement >= 0) {
                    selectedIds.remove(selected.get(replacement).id());
                    selected.set(replacement, best.document());
                } else if (selected.size() < maxLimit) {
                    selected.add(best.document());
                } else {
                    return;
                }
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
        Set<String> existingOfficial = officialCoverage(selected, resourceTypes);
        for (int index = selected.size() - 1; index >= 0; index--) {
            if (PROJECT_DECISION.equals(selected.get(index).authority())) continue;
            List<ReferenceDocument> proposed = replacing(selected, index, replacement);
            if (coveredResourceTypes(proposed, resourceTypes).containsAll(existingCoverage)
                    && officialCoverage(proposed, resourceTypes).containsAll(existingOfficial)) {
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
                && providerCoverage(proposed, resourceTypes).containsAll(existingProviders)
                && officialCoverage(proposed, resourceTypes).containsAll(officialCoverage(selected, resourceTypes));
    }

    private Set<String> officialCoverage(List<ReferenceDocument> documents, List<String> resourceTypes) {
        return coveredResourceTypes(documents.stream()
                .filter(ReferenceDocument::isOfficialProviderDocumentation).toList(), resourceTypes);
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
