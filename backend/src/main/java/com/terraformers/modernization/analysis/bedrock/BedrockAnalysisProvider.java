package com.terraformers.modernization.analysis.bedrock;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisObservability;
import com.terraformers.modernization.analysis.AnalysisProvider;
import com.terraformers.modernization.analysis.AnalysisProviderFailureException;
import com.terraformers.modernization.analysis.AnalysisProviderFailureReason;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.analysis.AnalysisResult;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.reference.ArchitectureFactsExtractor;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.BedrockArchitectureFactsExtractor;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.ReferenceRetriever;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectReader;
import com.terraformers.modernization.storage.ObjectReference;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Component
@Lazy
public class BedrockAnalysisProvider implements AnalysisProvider {

    private static final Logger log = LoggerFactory.getLogger(BedrockAnalysisProvider.class);

    private final ObjectReader objectReader;
    private final ReferenceRetriever referenceRetriever;
    private final AnalysisRuntimeProperties properties;
    private final BedrockRuntimeProperties bedrockProperties;
    private final ArchitectureFactsExtractor factsExtractor;
    private final RetrievalQueryTextBuilder queryTextBuilder;
    private final AnalysisObservability observability;
    private final AnalysisGenerationStage generationStage;

    @Autowired
    public BedrockAnalysisProvider(
            ObjectReader objectReader,
            ReferenceRetriever referenceRetriever,
            AnalysisRuntimeProperties properties,
            BedrockRuntimeProperties bedrockProperties,
            BedrockArchitectureFactsExtractor factsExtractor,
            RetrievalQueryTextBuilder queryTextBuilder,
            AnalysisObservability observability,
            BedrockGenerationStage generationStage
    ) {
        this.objectReader = objectReader;
        this.referenceRetriever = referenceRetriever;
        this.properties = properties;
        this.bedrockProperties = bedrockProperties;
        this.factsExtractor = factsExtractor;
        this.queryTextBuilder = queryTextBuilder;
        this.observability = observability;
        this.generationStage = generationStage;
    }

    public BedrockAnalysisProvider(
            BedrockRuntimeClient bedrockRuntimeClient,
            ObjectReader objectReader,
            ReferenceRetriever referenceRetriever,
            AnalysisRuntimeProperties properties,
            BedrockRuntimeProperties bedrockProperties,
            BedrockPromptBuilder promptBuilder,
            BedrockResponseParser responseParser,
            BedrockArchitectureFactsExtractor factsExtractor,
            RetrievalQueryTextBuilder queryTextBuilder,
            AnalysisObservability observability
    ) {
        this(
                objectReader,
                referenceRetriever,
                properties,
                bedrockProperties,
                factsExtractor,
                queryTextBuilder,
                observability,
                new BedrockGenerationStage(
                        bedrockRuntimeClient,
                        bedrockProperties,
                        promptBuilder,
                        responseParser,
                        observability
                )
        );
    }

    @Override
    public AnalysisResult analyze(AnalysisRequestContext context) {
        try {
            return analyzeWithBedrock(context);
        } catch (BedrockOutputTruncatedException exception) {
            throw new AnalysisProviderFailureException(
                    AnalysisProviderFailureReason.OUTPUT_TRUNCATED, exception);
        } catch (AnalysisInputRejectedException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            throw new AnalysisProviderFailureException(
                    AnalysisProviderFailureReason.INPUT_REJECTED, cause);
        } catch (BedrockResponseFormatException exception) {
            throw new AnalysisProviderFailureException(
                    AnalysisProviderFailureReason.RESPONSE_FORMAT, exception);
        }
    }

    private AnalysisResult analyzeWithBedrock(AnalysisRequestContext context) {
        long analysisStartedAt = System.nanoTime();
        requireModelId();

        ObjectContent source = objectReader.readContent(new ObjectReference(
                context.sourceBucket(),
                context.sourceKey()
        ));

        List<ReferenceDocument> references = retrieveReferences(source);
        AnalysisGenerationResult generated = generationStage.generate(context, source, references);

        AnalysisResult result = new AnalysisResult(
                generated.provider(),
                generated.terraformCode(),
                generated.summary(),
                generated.components(),
                generated.relationships(),
                generated.warnings(),
                references.stream().map(ReferenceDocument::id).toList()
        );
        log.info(
                "analysis pipeline outcome=success corpusVersion={} providerVersion={} referenceCount={} elapsedMs={}",
                properties.getCorpusVersion(),
                properties.getProviderVersion(),
                references.size(),
                (System.nanoTime() - analysisStartedAt) / 1_000_000
        );
        return result;
    }

    private List<ReferenceDocument> retrieveReferences(ObjectContent source) {
        RetrievalMode mode = properties.getRetrievalMode();
        if (mode == null) {
            throw new IllegalStateException("terraformers.analysis.retrieval-mode must be set");
        }
        if (mode == RetrievalMode.DISABLED) {
            return List.of();
        }
        long started = System.nanoTime();
        try {
            long factsStartedAt = System.nanoTime();
            ArchitectureRetrievalFacts facts = factsExtractor.extract(source);
            long factsElapsedMs = (System.nanoTime() - factsStartedAt) / 1_000_000;
            long searchStartedAt = System.nanoTime();
            List<ReferenceDocument> references = observability.recordAoss(() -> referenceRetriever.retrieve(
                    new ReferenceQuery(queryTextBuilder.build(facts), properties.getOpensearchTopK())
            ));
            observability.retrievedHits(references.size());
            long searchElapsedMs = (System.nanoTime() - searchStartedAt) / 1_000_000;
            log.info(
                    "reference retrieval outcome=success mode={} corpusVersion={} providerVersion={} topK={} referenceCount={} factsElapsedMs={} searchElapsedMs={} elapsedMs={}",
                    mode,
                    properties.getCorpusVersion(),
                    properties.getProviderVersion(),
                    properties.getOpensearchTopK(),
                    references.size(),
                    factsElapsedMs,
                    searchElapsedMs,
                    (System.nanoTime() - started) / 1_000_000
            );
            return references;
        } catch (RuntimeException exception) {
            log.warn(
                    "reference retrieval outcome=failure mode={} corpusVersion={} providerVersion={} topK={} errorClass={} elapsedMs={}",
                    mode,
                    properties.getCorpusVersion(),
                    properties.getProviderVersion(),
                    properties.getOpensearchTopK(),
                    exception.getClass().getSimpleName(),
                    (System.nanoTime() - started) / 1_000_000
            );
            if (mode == RetrievalMode.REQUIRED) {
                throw exception;
            }
            return List.of();
        }
    }

    private String requireModelId() {
        if (bedrockProperties.getModelId() == null || bedrockProperties.getModelId().isBlank()) {
            throw new IllegalStateException(
                    "terraformers.analysis.bedrock.model-id must be set when Bedrock provider is enabled");
        }
        return bedrockProperties.getModelId().strip();
    }
}
