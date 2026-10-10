package com.terraformers.modernization.analysis.vertex;

import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisDiagnosticEvidence;
import com.terraformers.modernization.analysis.AnalysisProvider;
import com.terraformers.modernization.analysis.AnalysisProviderFailureException;
import com.terraformers.modernization.analysis.AnalysisProviderFailureReason;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.AnalysisResult;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.analysis.GeneratedTerraformContractInspector;
import com.terraformers.modernization.analysis.EvidenceQualityAssessment;
import com.terraformers.modernization.analysis.EvidenceQualityAssessor;
import com.terraformers.modernization.analysis.EvidenceQualityAssessor.ProjectDecisionApplicability;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.ReferenceRetriever;
import com.terraformers.modernization.reference.OfficialKnowledgeCoverageCatalog;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import com.terraformers.modernization.reference.VertexArchitectureFactsExtractor;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectReader;
import com.terraformers.modernization.storage.ObjectReference;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class VertexAnalysisProvider implements AnalysisProvider {

    private static final Logger log = LoggerFactory.getLogger(VertexAnalysisProvider.class);
    private final ObjectReader objectReader;
    private final ReferenceRetriever referenceRetriever;
    private final AnalysisRuntimeProperties properties;
    private final VertexArchitectureFactsExtractor factsExtractor;
    private final RetrievalQueryTextBuilder queryTextBuilder;
    private final VertexGroundedGenerationOrchestrator groundedGeneration;
    private final AwsProviderSchemaCatalog schemaCatalog;
    private final EvidenceQualityAssessor qualityAssessor;
    private final OfficialKnowledgeCoverageCatalog knowledgeCoverage;

    @Autowired
    public VertexAnalysisProvider(
            ObjectReader objectReader,
            ReferenceRetriever referenceRetriever,
            AnalysisRuntimeProperties properties,
            VertexArchitectureFactsExtractor factsExtractor,
            RetrievalQueryTextBuilder queryTextBuilder,
            VertexGroundedGenerationOrchestrator groundedGeneration,
            AwsProviderSchemaCatalog schemaCatalog,
            EvidenceQualityAssessor qualityAssessor,
            OfficialKnowledgeCoverageCatalog knowledgeCoverage
    ) {
        this.objectReader = objectReader;
        this.referenceRetriever = referenceRetriever;
        this.properties = properties;
        this.factsExtractor = factsExtractor;
        this.queryTextBuilder = queryTextBuilder;
        this.groundedGeneration = groundedGeneration;
        this.schemaCatalog = schemaCatalog;
        this.qualityAssessor = qualityAssessor;
        this.knowledgeCoverage = knowledgeCoverage;
    }

    public VertexAnalysisProvider(ObjectReader objectReader, ReferenceRetriever referenceRetriever,
            AnalysisRuntimeProperties properties, VertexArchitectureFactsExtractor factsExtractor,
            RetrievalQueryTextBuilder queryTextBuilder, VertexGenerationStage generationStage,
            AwsProviderSchemaCatalog schemaCatalog, GeneratedTerraformContractInspector contractInspector,
            EvidenceQualityAssessor qualityAssessor, OfficialKnowledgeCoverageCatalog knowledgeCoverage) {
        this(objectReader, referenceRetriever, properties, factsExtractor, queryTextBuilder,
                new VertexGroundedGenerationOrchestrator(generationStage, referenceRetriever, properties,
                        schemaCatalog, contractInspector), schemaCatalog, qualityAssessor, knowledgeCoverage);
    }

    public VertexAnalysisProvider(ObjectReader objectReader, ReferenceRetriever referenceRetriever,
            AnalysisRuntimeProperties properties, VertexArchitectureFactsExtractor factsExtractor,
            RetrievalQueryTextBuilder queryTextBuilder, VertexGenerationStage generationStage,
            AwsProviderSchemaCatalog schemaCatalog, GeneratedTerraformContractInspector contractInspector) {
        this(objectReader, referenceRetriever, properties, factsExtractor, queryTextBuilder, generationStage,
                schemaCatalog, contractInspector, null, null);
    }

    @Override
    public AnalysisResult analyze(AnalysisRequestContext context) {
        try {
            return analyzeWithVertex(context);
        } catch (VertexOutputTruncatedException exception) {
            throw new AnalysisProviderFailureException(
                    AnalysisProviderFailureReason.OUTPUT_TRUNCATED, exception);
        } catch (AnalysisInputRejectedException exception) {
            throw new AnalysisProviderFailureException(
                    AnalysisProviderFailureReason.INPUT_REJECTED, exception);
        } catch (VertexResponseFormatException exception) {
            throw new AnalysisProviderFailureException(
                    AnalysisProviderFailureReason.RESPONSE_FORMAT, exception);
        }
    }

    private AnalysisResult analyzeWithVertex(AnalysisRequestContext context) {
        AnalysisDiagnosticEvidence.stage("source_read");
        ObjectContent source = objectReader.readContent(new ObjectReference(
                context.sourceBucket(),
                context.sourceKey()
        ));
        if (AnalysisDiagnosticEvidence.current() != null) AnalysisDiagnosticEvidence.current().source(source.bytes());
        RetrievalOutcome retrieval = retrieveReferences(source);
        AnalysisDiagnosticEvidence.stage("initial_generation");
        var outcome = groundedGeneration.generate(context, source, retrieval.facts(), retrieval.references(), update -> {
            var evidence = AnalysisDiagnosticEvidence.current();
            if (evidence == null) return;
            evidence.candidate("initial", update.firstDraftTerraform());
        });
        var generated = outcome.firstGeneration();
        List<ReferenceDocument> references = outcome.finalReferences();
        String terraform = outcome.finalTerraform();
        if (AnalysisDiagnosticEvidence.current() != null) {
            AnalysisDiagnosticEvidence.current().candidate("final", terraform);
            AnalysisDiagnosticEvidence.current().references("final_evidence", references);
        }
        AnalysisDiagnosticEvidence.stage("quality_assessment");
        EvidenceQualityAssessment quality = assess(retrieval, references, terraform);
        if (AnalysisDiagnosticEvidence.current() != null) AnalysisDiagnosticEvidence.current().captured("quality_assessment");
        // Repair returns HCL only. Retain initial uncertainties without presenting them as final-code findings.
        List<String> warnings = new ArrayList<>(generated.warnings().stream()
                .map(warning -> outcome.repairAttempted()
                        ? "Initial draft (before grounding repair; not revalidated): " + warning : warning)
                .toList());
        if (quality != null) {
            List<String> extractedUnknown = quality.extractedResourceTypes().stream()
                    .filter(type -> !schemaCatalog.contains(type)).toList();
            log.info("Vertex evidence quality technicalStatus={} knowledgeStatus={} qualityStatus={} "
                            + "projectDecisionStatus={} extractedUnknownToProvider={} generatedAbsentFromProvider={} "
                            + "generatedWithoutSelectedEvidence={}",
                    quality.technicalStatus(), quality.knowledgeStatus(), quality.qualityStatus(),
                    quality.projectDecisionStatus(), safeResourceTypes(extractedUnknown),
                    safeResourceTypes(quality.generatedResourcesAbsentFromProviderSchema()),
                    safeResourceTypes(quality.generatedResourcesWithoutSelectedEvidence()));
            if (!extractedUnknown.isEmpty()) {
                warnings.add("Image resource candidates not recognized by the provider: "
                        + safeResourceTypes(extractedUnknown) + ". Generated Terraform validation is a separate check.");
            }
            if (!quality.generatedResourcesWithoutSelectedEvidence().isEmpty()) {
                warnings.add("Generated Terraform types lack selected official documentation metadata: "
                        + safeResourceTypes(quality.generatedResourcesWithoutSelectedEvidence())
                        + ". Provider-valid implementation support still requires evidence review.");
            }
        }
        return new AnalysisResult(
                generated.provider(),
                terraform,
                generated.summary(),
                generated.components(),
                generated.relationships(),
                warnings,
                references.stream().map(ReferenceDocument::id).toList(),
                quality
        );
    }

    private List<String> safeResourceTypes(List<String> types) {
        // Type identifiers only, never source images, model prose, HCL, values or error messages.
        return types.stream().filter(type -> type.matches("aws_[a-z0-9_]{1,80}"))
                .distinct().limit(64).toList();
    }

    private EvidenceQualityAssessment assess(RetrievalOutcome retrieval,
            List<ReferenceDocument> references, String terraform) {
        if (qualityAssessor == null || knowledgeCoverage == null) return null;
        List<String> resources = retrieval.facts().resourceTypes();
        return qualityAssessor.assess(new EvidenceQualityAssessor.Input(
                EvidenceQualityAssessment.TechnicalStatus.PASS,
                com.terraformers.modernization.analysis.AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                resources, knowledgeCoverage.availableFor(resources, schemaCatalog), references,
                terraform, ProjectDecisionApplicability.UNKNOWN, List.of()));
    }

    private RetrievalOutcome retrieveReferences(ObjectContent source) {
        RetrievalMode mode = properties.getRetrievalMode();
        if (mode == null) {
            throw new IllegalStateException("terraformers.analysis.retrieval-mode must be set");
        }
        if (mode == RetrievalMode.DISABLED) {
            return new RetrievalOutcome(new ArchitectureRetrievalFacts("", List.of(), List.of(), List.of()), List.of());
        }
        AnalysisDiagnosticEvidence.stage("facts");
        ArchitectureRetrievalFacts facts = factsExtractor.extract(source);
        if (AnalysisDiagnosticEvidence.current() != null) AnalysisDiagnosticEvidence.current().facts(facts);
        AnalysisDiagnosticEvidence.stage("retrieval");
        ReferenceQuery query = new ReferenceQuery(
                queryTextBuilder.build(facts),
                facts.resourceTypes(),
                properties.getOpensearchMaxEvidence()
        );
        try {
            List<ReferenceDocument> references = referenceRetriever.retrieve(query);
            if (AnalysisDiagnosticEvidence.current() != null) AnalysisDiagnosticEvidence.current().references("retrieval", references);
            log.info(
                    "Vertex reference retrieval outcome=success mode={} corpusVersion={} referenceCount={}",
                    mode,
                    properties.getCorpusVersion(),
                    references.size()
            );
            return new RetrievalOutcome(facts, references);
        } catch (RuntimeException exception) {
            log.warn(
                    "Vertex reference retrieval outcome=failure mode={} corpusVersion={} errorClass={}",
                    mode,
                    properties.getCorpusVersion(),
                    exception.getClass().getSimpleName()
            );
            if (mode == RetrievalMode.REQUIRED) {
                throw exception;
            }
            return new RetrievalOutcome(facts, List.of());
        }
    }

    private record RetrievalOutcome(ArchitectureRetrievalFacts facts, List<ReferenceDocument> references) {}
}
