package com.terraformers.modernization.analysis.vertex;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
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
import com.terraformers.modernization.analysis.RequiredGroundingPolicy;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class VertexAnalysisProvider implements AnalysisProvider {

    private static final Logger log = LoggerFactory.getLogger(VertexAnalysisProvider.class);
    private static final Pattern REFERENCE_AWS_RESOURCE = Pattern.compile(
            "(?m)^\\s*resource\\s+\"(aws_[a-z0-9_]+)\"\\s+\"[^\"]+\"\\s*\\{");

    private final ObjectReader objectReader;
    private final ReferenceRetriever referenceRetriever;
    private final AnalysisRuntimeProperties properties;
    private final VertexArchitectureFactsExtractor factsExtractor;
    private final RetrievalQueryTextBuilder queryTextBuilder;
    private final VertexGenerationStage generationStage;
    private final AwsProviderSchemaCatalog schemaCatalog;
    private final GeneratedTerraformContractInspector contractInspector;
    private final EvidenceQualityAssessor qualityAssessor;
    private final OfficialKnowledgeCoverageCatalog knowledgeCoverage;

    @Autowired
    public VertexAnalysisProvider(
            ObjectReader objectReader,
            ReferenceRetriever referenceRetriever,
            AnalysisRuntimeProperties properties,
            VertexArchitectureFactsExtractor factsExtractor,
            RetrievalQueryTextBuilder queryTextBuilder,
            VertexGenerationStage generationStage,
            AwsProviderSchemaCatalog schemaCatalog,
            GeneratedTerraformContractInspector contractInspector,
            EvidenceQualityAssessor qualityAssessor,
            OfficialKnowledgeCoverageCatalog knowledgeCoverage
    ) {
        this.objectReader = objectReader;
        this.referenceRetriever = referenceRetriever;
        this.properties = properties;
        this.factsExtractor = factsExtractor;
        this.queryTextBuilder = queryTextBuilder;
        this.generationStage = generationStage;
        this.schemaCatalog = schemaCatalog;
        this.contractInspector = contractInspector;
        this.qualityAssessor = qualityAssessor;
        this.knowledgeCoverage = knowledgeCoverage;
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
        ObjectContent source = objectReader.readContent(new ObjectReference(
                context.sourceBucket(),
                context.sourceKey()
        ));
        RetrievalOutcome retrieval = retrieveReferences(source);
        List<ReferenceDocument> references = retrieval.references();
        AwsProviderSchemaEvidence schemaEvidence = schemaCatalog.resolve(
                promptSchemaCandidates(retrieval.facts(), references));
        AnalysisGenerationResult generated =
                generationStage.generate(context, source, references, schemaEvidence);
        RequiredGroundingPolicy.requireForArchitecture(
                properties.getRetrievalMode(), generated, references);
        contractInspector.inspect(generated.terraformCode());
        EvidenceQualityAssessment quality = assess(retrieval, generated.terraformCode());
        return new AnalysisResult(
                generated.provider(),
                generated.terraformCode(),
                generated.summary(),
                generated.components(),
                generated.relationships(),
                generated.warnings(),
                references.stream().map(ReferenceDocument::id).toList(),
                quality
        );
    }

    private EvidenceQualityAssessment assess(RetrievalOutcome retrieval, String terraform) {
        if (qualityAssessor == null || knowledgeCoverage == null) return null;
        List<String> resources = retrieval.facts().resourceTypes();
        return qualityAssessor.assess(new EvidenceQualityAssessor.Input(
                EvidenceQualityAssessment.TechnicalStatus.PASS,
                com.terraformers.modernization.analysis.AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                resources, knowledgeCoverage.availableFor(resources, schemaCatalog), retrieval.references(),
                terraform, ProjectDecisionApplicability.UNKNOWN, List.of()));
    }

    private Set<String> promptSchemaCandidates(
            ArchitectureRetrievalFacts facts,
            List<ReferenceDocument> references
    ) {
        Set<String> candidates = new LinkedHashSet<>();
        if (facts != null) {
            facts.resourceTypes().stream()
                    .filter(schemaCatalog::contains)
                    .forEach(candidates::add);
        }
        for (ReferenceDocument reference : references == null ? List.<ReferenceDocument>of() : references) {
            reference.resourceTypes().stream()
                    .filter(schemaCatalog::contains)
                    .forEach(candidates::add);
            Matcher matcher = REFERENCE_AWS_RESOURCE.matcher(reference.content() == null ? "" : reference.content());
            while (matcher.find()) {
                String resourceType = matcher.group(1);
                if (schemaCatalog.contains(resourceType)) {
                    candidates.add(resourceType);
                }
            }
        }
        return candidates;
    }

    private RetrievalOutcome retrieveReferences(ObjectContent source) {
        RetrievalMode mode = properties.getRetrievalMode();
        if (mode == null) {
            throw new IllegalStateException("terraformers.analysis.retrieval-mode must be set");
        }
        if (mode == RetrievalMode.DISABLED) {
            return new RetrievalOutcome(new ArchitectureRetrievalFacts("", List.of(), List.of(), List.of()), List.of());
        }
        ArchitectureRetrievalFacts facts = factsExtractor.extract(source);
        ReferenceQuery query = new ReferenceQuery(
                queryTextBuilder.build(facts),
                facts.resourceTypes(),
                properties.getOpensearchMaxEvidence()
        );
        try {
            List<ReferenceDocument> references = referenceRetriever.retrieve(query);
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
