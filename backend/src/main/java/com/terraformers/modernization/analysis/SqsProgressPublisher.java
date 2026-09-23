package com.terraformers.modernization.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.sqs.SqsRuntimeProperties;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Component
@ConditionalOnProperty(prefix = "terraformers.analysis", name = "progress-publisher", havingValue = "sqs")
public class SqsProgressPublisher implements ProgressPublisher {

    private static final Logger log = LoggerFactory.getLogger(SqsProgressPublisher.class);

    private final Supplier<SqsClient> sqsClientSupplier;
    private final ObjectMapper objectMapper;
    private final SqsRuntimeProperties properties;

    @Autowired
    public SqsProgressPublisher(
            ObjectProvider<SqsClient> sqsClientProvider,
            ObjectMapper objectMapper,
            SqsRuntimeProperties properties
    ) {
        this(
                () -> sqsClientProvider.getIfAvailable(() -> SqsClient.builder().build()),
                objectMapper,
                properties
        );
    }

    SqsProgressPublisher(
            Supplier<SqsClient> sqsClientSupplier,
            ObjectMapper objectMapper,
            SqsRuntimeProperties properties
    ) {
        this.sqsClientSupplier = sqsClientSupplier;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void publish(ProgressEvent event) {
        String queueUrl = normalizeQueueUrl(properties.getProgressQueueUrl());
        if (queueUrl == null) {
            log.warn("SQS progress publisher is selected but terraformers.analysis.sqs.progress-queue-url is not configured. jobId={}", event.jobId());
            return;
        }

        try {
            sqsClientSupplier.get().sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(toJson(event))
                    .build());
            log.info("published analysis progress event to SQS jobId={} status={}", event.jobId(), event.status());
        } catch (RuntimeException exception) {
            log.warn("failed to publish analysis progress event to SQS jobId={} status={}",
                    event.jobId(), event.status(), exception);
        }
    }

    private String toJson(ProgressEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("failed to serialize progress event", exception);
        }
    }

    private String normalizeQueueUrl(String queueUrl) {
        if (queueUrl == null || queueUrl.isBlank()) {
            return null;
        }
        return queueUrl.strip();
    }
}
