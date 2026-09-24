package com.terraformers.modernization.analysis.vertex;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisProvider;
import com.terraformers.modernization.analysis.AnalysisProviderFailureException;
import com.terraformers.modernization.analysis.AnalysisProviderFailureReason;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.AnalysisResult;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.ReferenceRetriever;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import com.terraformers.modernization.reference.VertexArchitectureFactsExtractor;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectReader;
import com.terraformers.modernization.storage.ObjectReference;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
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
    private final VertexGenerationStage generationStage;

    public VertexAnalysisProvider(
            ObjectReader objectReader,
            ReferenceRetriever referenceRetriever,
            AnalysisRuntimeProperties properties,
            VertexArchitectureFactsExtractor factsExtractor,
            RetrievalQueryTextBuilder queryTextBuilder,
            VertexGenerationStage generationStage
    ) {
        this.objectReader = objectReader;
        this.referenceRetriever = referenceRetriever;
        this.properties = properties;
        this.factsExtractor = factsExtractor;
        this.queryTextBuilder = queryTextBuilder;
        this.generationStage = generationStage;
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
        List<ReferenceDocument> references = retrieveReferences(source);
        AnalysisGenerationResult generated = generationStage.generate(context, source, references);
        return new AnalysisResult(
                generated.provider(),
                generated.terraformCode(),
                generated.summary(),
                generated.components(),
                generated.relationships(),
                generated.warnings(),
                references.stream().map(ReferenceDocument::id).toList()
        );
    }

    private List<ReferenceDocument> retrieveReferences(ObjectContent source) {
        RetrievalMode mode = properties.getRetrievalMode();
        if (mode == null) {
            throw new IllegalStateException("terraformers.analysis.retrieval-mode must be set");
        }
        if (mode == RetrievalMode.DISABLED) {
            return List.of();
        }
        ArchitectureRetrievalFacts facts = factsExtractor.extract(source);
        ReferenceQuery query = new ReferenceQuery(
                queryTextBuilder.build(facts),
                properties.getOpensearchTopK()
        );
        try {
            List<ReferenceDocument> references = referenceRetriever.retrieve(query);
            log.info(
                    "Vertex reference retrieval outcome=success mode={} corpusVersion={} referenceCount={}",
                    mode,
                    properties.getCorpusVersion(),
                    references.size()
            );
            return references;
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
            return List.of();
        }
    }
}
