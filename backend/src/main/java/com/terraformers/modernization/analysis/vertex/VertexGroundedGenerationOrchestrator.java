package com.terraformers.modernization.analysis.vertex;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.analysis.GeneratedTerraformContractInspector;
import com.terraformers.modernization.analysis.RequiredGroundingPolicy;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.ReferenceRetriever;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.opensearch.ReferenceEvidenceSelector;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The single bounded generation/closure path used by production and broad-v4 evaluation. */
@Component
@Lazy
public class VertexGroundedGenerationOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(VertexGroundedGenerationOrchestrator.class);
    private static final Pattern REFERENCE_AWS_RESOURCE = Pattern.compile(
            "(?m)^\\s*resource\\s+\"(aws_[a-z0-9_]+)\"\\s+\"[^\"]+\"\\s*\\{");

    private final VertexGenerationStage generationStage;
    private final ReferenceRetriever referenceRetriever;
    private final AnalysisRuntimeProperties properties;
    private final AwsProviderSchemaCatalog schemaCatalog;
    private final GeneratedTerraformContractInspector contractInspector;

    public VertexGroundedGenerationOrchestrator(VertexGenerationStage generationStage,
            ReferenceRetriever referenceRetriever, AnalysisRuntimeProperties properties,
            AwsProviderSchemaCatalog schemaCatalog, GeneratedTerraformContractInspector contractInspector) {
        this.generationStage = generationStage;
        this.referenceRetriever = referenceRetriever;
        this.properties = properties;
        this.schemaCatalog = schemaCatalog;
        this.contractInspector = contractInspector;
    }

    public Outcome generate(AnalysisRequestContext context, ObjectContent source,
            ArchitectureRetrievalFacts facts, List<ReferenceDocument> initialReferences) {
        return generate(context, source, facts, initialReferences, outcome -> {});
    }

    /** Bounded snapshots let evaluation retain attempted closure/repair evidence on failure. */
    public Outcome generate(AnalysisRequestContext context, ObjectContent source,
            ArchitectureRetrievalFacts facts, List<ReferenceDocument> initialReferences,
            Consumer<Outcome> evidenceObserver) {
        List<ReferenceDocument> initial = initialReferences == null ? List.of() : List.copyOf(initialReferences);
        AwsProviderSchemaEvidence schemaEvidence = schemaCatalog.resolve(schemaCandidates(facts, initial));
        AnalysisGenerationResult generated = generationStage.generate(context, source, initial, schemaEvidence);
        Outcome outcome = new Outcome(generated, initial, null, initial, false,
                generated.terraformCode(), List.of());
        evidenceObserver.accept(outcome);
        if (generated.inputClassification() != AnalysisInputClassification.ARCHITECTURE_DIAGRAM) {
            throw new AnalysisInputRejectedException(generated.inputClassification(),
                    generated.classificationConfidence(), generated.retryOccurred(), null);
        }
        RequiredGroundingPolicy.requireForArchitecture(properties.getRetrievalMode(), generated, initial);
        List<String> generatedTypes = contractInspector.resourceTypes(generated.terraformCode()).stream()
                .filter(schemaCatalog::contains).toList();
        List<String> missing = missingOfficialEvidence(generatedTypes, initial);
        if (properties.getRetrievalMode() != RetrievalMode.DISABLED && !missing.isEmpty()) {
            ReferenceQuery query = new ReferenceQuery(
                    "Official AWS provider documentation for generated Terraform resources: " + String.join(", ", missing),
                    missing, properties.getOpensearchMaxEvidence(), true);
            outcome = new Outcome(generated, initial, new ClosureRetrieval(query, List.of()), initial,
                    false, generated.terraformCode(), missing);
            evidenceObserver.accept(outcome);
            List<ReferenceDocument> closure;
            long closureStarted = System.nanoTime();
            try {
                closure = referenceRetriever.retrieve(query);
            } catch (RuntimeException failure) {
                log.warn("Vertex grounding stage=closure outcome=failure finishReason=NOT_APPLICABLE "
                        + "outputTokens=NOT_APPLICABLE errorClass={}", failure.getClass().getSimpleName());
                throw failure;
            }
            log.info("Vertex grounding stage=closure outcome=success finishReason=NOT_APPLICABLE "
                            + "outputTokens=NOT_APPLICABLE hitCount={} elapsedMs={}",
                    closure.size(), (System.nanoTime() - closureStarted) / 1_000_000);
            outcome = new Outcome(generated, initial, new ClosureRetrieval(query, closure), initial,
                    false, generated.terraformCode(), missing);
            evidenceObserver.accept(outcome);
            Set<String> requested = new LinkedHashSet<>(facts.resourceTypes());
            requested.addAll(generatedTypes);
            List<ReferenceDocument> selected = new ReferenceEvidenceSelector().merge(initial, closure,
                    List.copyOf(requested), properties.getOpensearchMaxEvidence());
            Set<String> schemaCandidates = new LinkedHashSet<>(schemaEvidence.resourceTypes());
            schemaCandidates.addAll(generatedTypes);
            AwsProviderSchemaEvidence expandedSchema = schemaCatalog.resolve(schemaCandidates);
            outcome = new Outcome(generated, initial, new ClosureRetrieval(query, closure), selected,
                    true, generated.terraformCode(), missingOfficialEvidence(generatedTypes, selected));
            evidenceObserver.accept(outcome);
            String repaired = generationStage.repair(facts, generated, selected, expandedSchema);
            outcome = new Outcome(generated, initial, outcome.closureRetrieval(), selected,
                    true, repaired, List.of());
            evidenceObserver.accept(outcome);
        }
        // Remaining gaps are recorded, never followed by another closure or repair.
        contractInspector.inspect(outcome.finalTerraform());
        List<String> finalTypes = contractInspector.resourceTypes(outcome.finalTerraform()).stream()
                .filter(schemaCatalog::contains).toList();
        outcome = new Outcome(generated, initial, outcome.closureRetrieval(), outcome.finalReferences(),
                outcome.repairAttempted(), outcome.finalTerraform(),
                missingOfficialEvidence(finalTypes, outcome.finalReferences()));
        evidenceObserver.accept(outcome);
        return outcome;
    }

    private List<String> missingOfficialEvidence(List<String> generatedTypes, List<ReferenceDocument> references) {
        Set<String> officialTypes = new LinkedHashSet<>();
        references.stream().filter(ReferenceDocument::isOfficialProviderDocumentation)
                .forEach(reference -> officialTypes.addAll(reference.resourceTypes()));
        return generatedTypes.stream().filter(type -> !officialTypes.contains(type)).toList();
    }

    public Set<String> schemaCandidates(ArchitectureRetrievalFacts facts, List<ReferenceDocument> references) {
        Set<String> candidates = new LinkedHashSet<>();
        if (facts != null) {
            facts.resourceTypes().stream().filter(schemaCatalog::contains).forEach(candidates::add);
        }
        for (ReferenceDocument reference : references == null ? List.<ReferenceDocument>of() : references) {
            reference.resourceTypes().stream().filter(schemaCatalog::contains).forEach(candidates::add);
            Matcher matcher = REFERENCE_AWS_RESOURCE.matcher(reference.content() == null ? "" : reference.content());
            while (matcher.find()) {
                String type = matcher.group(1);
                if (schemaCatalog.contains(type)) candidates.add(type);
            }
        }
        return candidates;
    }

    public record ClosureRetrieval(ReferenceQuery query, List<ReferenceDocument> references) {
        public ClosureRetrieval {
            references = List.copyOf(references);
        }
    }

    public record Outcome(AnalysisGenerationResult firstGeneration, List<ReferenceDocument> initialReferences,
            ClosureRetrieval closureRetrieval, List<ReferenceDocument> finalReferences,
            boolean repairAttempted, String finalTerraform, List<String> finalGeneratedResourceEvidenceGaps) {
        public Outcome {
            initialReferences = List.copyOf(initialReferences);
            finalReferences = List.copyOf(finalReferences);
            finalGeneratedResourceEvidenceGaps = List.copyOf(finalGeneratedResourceEvidenceGaps);
        }

        public String firstDraftTerraform() { return firstGeneration.terraformCode(); }
        public boolean closureAttempted() { return closureRetrieval != null; }
    }
}
