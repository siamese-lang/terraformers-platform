package com.terraformers.modernization.analysis.bedrock;

import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.AnalysisObservability;
import com.terraformers.modernization.analysis.AnalysisProviderTimeoutException;
import com.terraformers.modernization.analysis.AnalysisRequestContext;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Component
@Lazy
public class BedrockGenerationStage implements AnalysisGenerationStage {

    private static final Logger log = LoggerFactory.getLogger(BedrockGenerationStage.class);

    private final BedrockRuntimeClient client;
    private final BedrockRuntimeProperties properties;
    private final BedrockPromptBuilder promptBuilder;
    private final BedrockResponseParser responseParser;
    private final AnalysisObservability observability;

    public BedrockGenerationStage(
            BedrockRuntimeClient client,
            BedrockRuntimeProperties properties,
            BedrockPromptBuilder promptBuilder,
            BedrockResponseParser responseParser,
            AnalysisObservability observability
    ) {
        this.client = client;
        this.properties = properties;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.observability = observability;
    }

    @Override
    public AnalysisGenerationResult generate(
            AnalysisRequestContext context,
            ObjectContent source,
            List<ReferenceDocument> references
    ) {
        String modelId = requireModelId();
        List<ReferenceDocument> safeReferences = references == null ? List.of() : List.copyOf(references);
        try {
            ParsedBedrockAnalysis parsed = invokeAndParse(
                    context, source, safeReferences, modelId, 1, BedrockPromptMode.STANDARD);
            return result(modelId, parsed, false);
        } catch (ArchitectureInputRejectedException exception) {
            throw rejected(exception, false);
        } catch (BedrockOutputTruncatedException exception) {
            try {
                ParsedBedrockAnalysis parsed = invokeAndParse(
                        context, source, safeReferences, modelId, 2, BedrockPromptMode.COMPACT);
                return result(modelId, parsed, true);
            } catch (ArchitectureInputRejectedException rejected) {
                throw rejected(rejected, true);
            }
        }
    }

    private AnalysisGenerationResult result(String modelId, ParsedBedrockAnalysis parsed, boolean retryOccurred) {
        return new AnalysisGenerationResult(
                "bedrock:" + modelId,
                AnalysisInputClassification.valueOf(parsed.inputType().name()),
                parsed.classificationConfidence(),
                parsed.terraformCode(),
                parsed.summary(),
                parsed.components(),
                parsed.relationships(),
                parsed.warnings(),
                parsed.stopReason(),
                parsed.outputTokens(),
                retryOccurred
        );
    }

    private AnalysisInputRejectedException rejected(
            ArchitectureInputRejectedException exception,
            boolean retryOccurred
    ) {
        return new AnalysisInputRejectedException(
                AnalysisInputClassification.valueOf(exception.getInputType().name()),
                exception.getClassificationConfidence(),
                retryOccurred,
                exception
        );
    }

    private ParsedBedrockAnalysis invokeAndParse(
            AnalysisRequestContext context,
            ObjectContent source,
            List<ReferenceDocument> references,
            String modelId,
            int attempt,
            BedrockPromptMode promptMode
    ) {
        long startedAt = System.nanoTime();
        String requestBody = promptBuilder.buildClaudeVisionRequest(
                source, references, properties.getMaxTokens(), promptMode);
        try {
            InvokeModelResponse response = observability.recordBedrock(() -> client.invokeModel(InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(requestBody))
                    .build()));
            ParsedBedrockAnalysis parsed = responseParser.parse(response.body().asUtf8String());
            logCall(attempt, false, startedAt);
            return parsed;
        } catch (BedrockOutputTruncatedException exception) {
            logCall(attempt, true, startedAt);
            throw exception;
        } catch (ArchitectureInputRejectedException exception) {
            logRejectedCall(exception, startedAt);
            throw exception;
        } catch (ApiCallAttemptTimeoutException | ApiCallTimeoutException exception) {
            logFailedCall(exception, startedAt);
            throw new AnalysisProviderTimeoutException(exception);
        } catch (SdkClientException exception) {
            logFailedCall(exception, startedAt);
            if (hasReadTimeout(exception)) {
                throw new AnalysisProviderTimeoutException(exception);
            }
            throw exception;
        } catch (RuntimeException exception) {
            logFailedCall(exception, startedAt);
            throw exception;
        }
    }

    private boolean hasReadTimeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().toLowerCase().contains("read timed out")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void logRejectedCall(ArchitectureInputRejectedException exception, long startedAt) {
        log.warn(
                "Bedrock analysis call rejected outcome=REJECTED errorType={} elapsedMs={}",
                exception.getClass().getSimpleName(),
                elapsedMillis(startedAt)
        );
    }

    private void logCall(int attempt, boolean truncated, long startedAt) {
        if (truncated) {
            log.warn(
                    "Bedrock analysis call completed with truncation retry={} elapsedMs={}",
                    attempt == 1,
                    elapsedMillis(startedAt)
            );
            return;
        }
        log.info("Bedrock analysis call completed outcome=SUCCESS elapsedMs={}", elapsedMillis(startedAt));
    }

    private void logFailedCall(RuntimeException exception, long startedAt) {
        log.warn(
                "Bedrock analysis call failed outcome=FAILED errorType={} elapsedMs={}",
                exception.getClass().getSimpleName(),
                elapsedMillis(startedAt)
        );
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private String requireModelId() {
        if (properties.getModelId() == null || properties.getModelId().isBlank()) {
            throw new IllegalStateException(
                    "terraformers.analysis.bedrock.model-id must be set when Bedrock provider is enabled");
        }
        return properties.getModelId().strip();
    }
}
