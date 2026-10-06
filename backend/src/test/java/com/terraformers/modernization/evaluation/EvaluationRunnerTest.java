package com.terraformers.modernization.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisGenerationOutputTruncatedException;
import com.terraformers.modernization.analysis.AnalysisGenerationResponseFormatException;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.TerraformDraftValidation;
import com.terraformers.modernization.analysis.TerraformDraftValidator;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationDataset;
import com.terraformers.modernization.evaluation.EvaluationTrace.ConfigurationIdentity;
import com.terraformers.modernization.reference.ArchitectureFactsExtractor;
import com.terraformers.modernization.reference.ArchitectureFactsExtractionException;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceQuery;
import com.terraformers.modernization.reference.ReferenceRetriever;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationRunnerTest {

    @Test
    void classifiesTerraformValidationFailureModes() {
        assertThat(EvaluationRunner.validationFailureCategory(
                new TerraformDraftValidation(false, "", "AWS_PROVIDER_CONTRACT: UNKNOWN_RESOURCE")))
                .isEqualTo(EvaluationFailureCategory.TERRAFORM_PROVIDER_CONTRACT);
        assertThat(EvaluationRunner.validationFailureCategory(
                new TerraformDraftValidation(false, "", "INIT_CONFIGURATION: Terraform initialization/configuration failed")))
                .isEqualTo(EvaluationFailureCategory.TERRAFORM_INIT_CONFIGURATION);
        assertThat(EvaluationRunner.validationFailureCategory(
                new TerraformDraftValidation(false, "", "VALIDATE_CONFIGURATION: generated Terraform failed Terraform CLI validation")))
                .isEqualTo(EvaluationFailureCategory.TERRAFORM_EXECUTABLE_VALIDATION);
        assertThat(EvaluationRunner.validationFailureCategory(
                new TerraformDraftValidation(false, "", "Terraform must contain at least one resource")))
                .isEqualTo(EvaluationFailureCategory.TERRAFORM_STRUCTURAL_VALIDATION);
    }

    @Test
    void mapsEveryProviderNeutralPartialFailureCategory() {
        EvaluationRunner runner = runner(source -> new ArchitectureRetrievalFacts("", List.of(), List.of(), List.of()),
                retriever(), generator());

        assertThat(runner.generationFailureCategory(providerFailure(
                com.terraformers.modernization.analysis.AnalysisProviderFailureReason.OUTPUT_TRUNCATED)))
                .isEqualTo(EvaluationFailureCategory.OUTPUT_TRUNCATED);
        assertThat(runner.generationFailureCategory(providerFailure(
                com.terraformers.modernization.analysis.AnalysisProviderFailureReason.CONTENT_BLOCKED)))
                .isEqualTo(EvaluationFailureCategory.PROVIDER_CONTENT_BLOCKED);
        assertThat(runner.generationFailureCategory(providerFailure(
                com.terraformers.modernization.analysis.AnalysisProviderFailureReason.EMPTY_RESPONSE)))
                .isEqualTo(EvaluationFailureCategory.PROVIDER_EMPTY_RESPONSE);
        assertThat(runner.generationFailureCategory(new com.terraformers.modernization.analysis.AnalysisProviderTimeoutException(
                new RuntimeException())))
                .isEqualTo(EvaluationFailureCategory.PROVIDER_TIMEOUT);
        assertThat(runner.generationFailureCategory(providerFailure(
                com.terraformers.modernization.analysis.AnalysisProviderFailureReason.RATE_LIMITED)))
                .isEqualTo(EvaluationFailureCategory.PROVIDER_RATE_LIMITED);
        assertThat(runner.generationFailureCategory(providerFailure(
                com.terraformers.modernization.analysis.AnalysisProviderFailureReason.PROVIDER_ERROR)))
                .isEqualTo(EvaluationFailureCategory.PROVIDER_ERROR);
        assertThat(runner.generationFailureCategory(providerFailure(
                com.terraformers.modernization.analysis.AnalysisProviderFailureReason.RESPONSE_FORMAT)))
                .isEqualTo(EvaluationFailureCategory.RESPONSE_FORMAT);
    }

    private com.terraformers.modernization.analysis.AnalysisProviderFailureException providerFailure(
            com.terraformers.modernization.analysis.AnalysisProviderFailureReason reason) {
        return new com.terraformers.modernization.analysis.AnalysisProviderFailureException(
                reason, new RuntimeException("SENTINEL_PROVIDER_PAYLOAD"));
    }

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void passesStructuredFactResourceTypesToRetrievalWithoutParsingQueryText() {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());
        AtomicReference<ReferenceQuery> receivedQuery = new AtomicReference<>();
        ReferenceRetriever capturingRetriever = query -> {
            if (receivedQuery.get() == null) {
                receivedQuery.set(query);
            }
            return List.of();
        };
        RetrievalQueryTextBuilder textWithoutResourceIdentifiers = new RetrievalQueryTextBuilder() {
            @Override
            public String build(ArchitectureRetrievalFacts facts) {
                return "architecture facts without resource identifiers";
            }
        };
        ConfigurationIdentity configuration = new ConfigurationIdentity(
                "terraformers-reference-v2", "5.100.0", "bedrock", "bedrock",
                RetrievalMode.REQUIRED.name(), 5, "stub-generation-model", "stub-embedding-model",
                "stub-config-sha256"
        );
        EvaluationRunner runner = new EvaluationRunner(
                source -> new ArchitectureRetrievalFacts(
                        "Three tier", List.of("VPC", "Database"), List.of("VPC -> Database"),
                        List.of("aws_vpc", "aws_db_instance", "aws_security_group")),
                textWithoutResourceIdentifiers,
                capturingRetriever,
                generator(),
                new TerraformDraftValidator(),
                RetrievalMode.REQUIRED,
                configuration
        );

        runner.run(dataset, "structured-resource-types");

        assertThat(receivedQuery.get()).isNotNull();
        assertThat(receivedQuery.get().text()).doesNotContain("aws_");
        assertThat(receivedQuery.get().resourceTypes())
                .containsExactly("aws_vpc", "aws_db_instance", "aws_security_group");
    }

    @Test
    void reportsInvalidExplicitResourceTypeAsRetrievalQueryConstructionFailure() {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());
        ArchitectureFactsExtractor invalidFacts = source -> new ArchitectureRetrievalFacts(
                "VPC", List.of("VPC"), List.of(), List.of("AWS::EC2::VPC"));

        EvaluationTrace trace = trace(
                runner(invalidFacts, retriever(), generator()).run(dataset, "invalid-resource-type"),
                "arch-vpc-three-tier"
        );

        assertThat(trace.retrieval().status()).isEqualTo(EvaluationStageStatus.FAIL);
        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.RETRIEVAL);
        assertThat(trace.firstDivergence().category())
                .isEqualTo(EvaluationFailureCategory.RETRIEVAL_QUERY_CONSTRUCTION);
        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.NOT_RUN);
    }

    @Test
    void executesFixedDatasetAndWritesOneMachineReadableRun(@TempDir Path tempDir) throws Exception {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());
        EvaluationRunner runner = runner(retriever(), generator());

        EvaluationRunResult result = runner.run(dataset, "stub-run-001");
        Path output = new EvaluationResultWriter(objectMapper).write(
                tempDir.resolve("terraformers-eval-v1.json"),
                result
        );

        assertThat(result.traces()).hasSize(6);
        assertThat(result.traces())
                .extracting(EvaluationTrace::caseId)
                .containsExactlyElementsOf(dataset.dataset().cases().stream().map(EvaluationCase::caseId).toList());

        EvaluationTrace positive = trace(result, "arch-private-aoss");
        assertThat(positive.factExtraction().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(positive.retrieval().evidence().queryText()).contains("OpenSearch");
        assertThat(positive.retrieval().evidence().hits()).hasSize(1);
        assertThat(positive.retrieval().evidence().hits().get(0).documentId()).isEqualTo("stub-reference");
        assertThat(positive.generation().evidence().suppliedReferenceIds()).containsExactly("stub-reference");
        assertThat(positive.generation().evidence().observedClassification())
                .isEqualTo(EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM);
        assertThat(positive.generation().evidence().generatedResourceTypes()).containsExactly("aws_vpc");
        assertThat(positive.validation().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(positive.firstDivergence()).isNull();

        EvaluationTrace ambiguous = trace(result, "ambiguous-cropped-service-sketch");
        assertThat(ambiguous.generation().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(ambiguous.generation().evidence().observedClassification())
                .isEqualTo(EvaluationCase.InputClassification.AMBIGUOUS);
        assertThat(ambiguous.validation().status()).isEqualTo(EvaluationStageStatus.NOT_RUN);

        EvaluationTrace nonArchitecture = trace(result, "non-architecture-deployment-dashboard");
        assertThat(nonArchitecture.generation().evidence().observedClassification())
                .isEqualTo(EvaluationCase.InputClassification.NON_ARCHITECTURE_IMAGE);
        assertThat(nonArchitecture.validation().status()).isEqualTo(EvaluationStageStatus.NOT_RUN);

        JsonNode json = objectMapper.readTree(Files.readString(output));
        assertThat(json.path("datasetVersion").asText()).isEqualTo("terraformers-eval-v1");
        assertThat(json.path("traces").size()).isEqualTo(6);
    }

    @Test
    void localizesRequiredRetrievalFailureBeforeGeneration() {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());
        ReferenceRetriever failingRetriever = query -> {
            throw new IllegalStateException("search unavailable");
        };

        EvaluationRunResult result = runner(failingRetriever, generator()).run(dataset, "retrieval-failure");
        EvaluationTrace trace = trace(result, "arch-vpc-three-tier");

        assertThat(trace.factExtraction().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(trace.retrieval().status()).isEqualTo(EvaluationStageStatus.FAIL);
        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.NOT_RUN);
        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.RETRIEVAL);
        assertThat(trace.firstDivergence().category()).isEqualTo(EvaluationFailureCategory.RETRIEVAL_FAILURE);
    }

    @Test
    void successfulEmptyRetrievalAllowsNegativeControlsToReachClassification() {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());
        AtomicInteger generationCalls = new AtomicInteger();
        AnalysisGenerationStage countingGenerator = (context, source, references) -> {
            generationCalls.incrementAndGet();
            return generator().generate(context, source, references);
        };

        EvaluationRunResult result = runner(query -> List.of(), countingGenerator)
                .run(dataset, "empty-negative-controls");

        EvaluationTrace nonArchitecture = trace(result, "non-architecture-deployment-dashboard");
        assertThat(nonArchitecture.retrieval().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(nonArchitecture.retrieval().evidence().hits()).isEmpty();
        assertThat(nonArchitecture.generation().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(nonArchitecture.generation().evidence().observedClassification())
                .isEqualTo(EvaluationCase.InputClassification.NON_ARCHITECTURE_IMAGE);
        assertThat(nonArchitecture.firstDivergence()).isNull();

        EvaluationTrace ambiguous = trace(result, "ambiguous-cropped-service-sketch");
        assertThat(ambiguous.retrieval().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(ambiguous.retrieval().evidence().hits()).isEmpty();
        assertThat(ambiguous.generation().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(ambiguous.generation().evidence().observedClassification())
                .isEqualTo(EvaluationCase.InputClassification.AMBIGUOUS);
        assertThat(ambiguous.firstDivergence()).isNull();
        assertThat(generationCalls).hasValue(dataset.dataset().cases().size());
    }

    @Test
    void requiredEmptyRetrievalFailsClosedAfterArchitectureClassification() {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());

        EvaluationTrace trace = trace(
                runner(query -> List.of(), generator()).run(dataset, "empty-architecture"),
                "arch-vpc-three-tier"
        );

        assertThat(trace.retrieval().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(trace.retrieval().evidence().hits()).isEmpty();
        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.FAIL);
        assertThat(trace.generation().evidence().observedClassification())
                .isEqualTo(EvaluationCase.InputClassification.ARCHITECTURE_DIAGRAM);
        assertThat(trace.generation().failures().get(0).category())
                .isEqualTo(EvaluationFailureCategory.RETRIEVAL_EMPTY);
        assertThat(trace.validation().status()).isEqualTo(EvaluationStageStatus.NOT_RUN);
        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.GENERATION);
        assertThat(trace.firstDivergence().category()).isEqualTo(EvaluationFailureCategory.RETRIEVAL_EMPTY);
    }

    @Test
    void classificationMismatchPrecedesRequiredEmptyGroundingFailure() {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());
        AnalysisGenerationStage incorrectArchitectureGenerator = (context, source, references) ->
                new AnalysisGenerationResult(
                        "stub",
                        AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                        0.95,
                        "resource \"aws_vpc\" \"main\" {}",
                        "incorrect architecture classification",
                        List.of("VPC"),
                        List.of(),
                        List.of(),
                        "end_turn",
                        100,
                        false
                );

        EvaluationTrace trace = trace(
                runner(query -> List.of(), incorrectArchitectureGenerator)
                        .run(dataset, "empty-classification-mismatch"),
                "non-architecture-deployment-dashboard"
        );

        assertThat(trace.retrieval().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(trace.retrieval().evidence().hits()).isEmpty();
        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.FAIL);
        assertThat(trace.generation().failures().get(0).category())
                .isEqualTo(EvaluationFailureCategory.INPUT_CLASSIFICATION);
        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.GENERATION);
        assertThat(trace.firstDivergence().category())
                .isEqualTo(EvaluationFailureCategory.INPUT_CLASSIFICATION);
        assertThat(trace.generation().failures())
                .noneMatch(failure -> failure.category() == EvaluationFailureCategory.RETRIEVAL_EMPTY);
    }

    @Test
    void recordsSanitizedFactFailureAndStopsRequiredPipelineAtFactExtraction() {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());
        String sensitive = "Bearer secret-token prompt image-base64";
        ArchitectureFactsExtractor failingFacts = source -> {
            throw ArchitectureFactsExtractionException.providerRuntime(
                    "429", "ClientException", true, new IllegalStateException(sensitive));
        };
        AtomicInteger retrievalCalls = new AtomicInteger();
        AtomicInteger generationCalls = new AtomicInteger();
        ReferenceRetriever retriever = query -> {
            retrievalCalls.incrementAndGet();
            return List.of();
        };
        AnalysisGenerationStage generator = (context, source, references) -> {
            generationCalls.incrementAndGet();
            throw new AssertionError("generation must not execute");
        };

        EvaluationRunResult result = runner(failingFacts, retriever, generator)
                .run(dataset, "fact-provider-failure");
        EvaluationTrace trace = trace(result, "arch-vpc-three-tier");

        assertThat(trace.factExtraction().status()).isEqualTo(EvaluationStageStatus.FAIL);
        assertThat(trace.factExtraction().failures().get(0).detail())
                .isEqualTo("reason=PROVIDER_RUNTIME;providerStatus=429;providerErrorType=ClientException;transient=true")
                .doesNotContain("secret-token", "prompt", "image", "base64");
        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.FACT_EXTRACTION);
        assertThat(trace.firstDivergence().category()).isEqualTo(EvaluationFailureCategory.PROVIDER_RUNTIME);
        assertThat(trace.retrieval().status()).isEqualTo(EvaluationStageStatus.NOT_RUN);
        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.NOT_RUN);
        assertThat(trace.validation().status()).isEqualTo(EvaluationStageStatus.NOT_RUN);
        assertThat(retrievalCalls).hasValue(0);
        assertThat(generationCalls).hasValue(0);
    }

    @Test
    void mapsFactResponseFailuresToExistingCategoriesWithStableSubtypeDetail() {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());
        ArchitectureFactsExtractor truncatedFacts = source -> {
            throw ArchitectureFactsExtractionException.response(
                    ArchitectureFactsExtractionException.Reason.RESPONSE_TRUNCATED, null);
        };

        EvaluationTrace trace = trace(
                runner(truncatedFacts, retriever(), generator()).run(dataset, "fact-truncated"),
                "arch-vpc-three-tier"
        );

        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.FACT_EXTRACTION);
        assertThat(trace.firstDivergence().category()).isEqualTo(EvaluationFailureCategory.OUTPUT_TRUNCATED);
        assertThat(trace.factExtraction().failures().get(0).detail())
                .isEqualTo("reason=RESPONSE_TRUNCATED");
    }

    @Test
    void classifiesProviderNeutralGenerationTruncation() {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());
        AnalysisGenerationStage truncatedGenerator = (context, source, references) -> {
            throw new AnalysisGenerationOutputTruncatedException("output limit reached");
        };

        EvaluationRunResult result = runner(retriever(), truncatedGenerator)
                .run(dataset, "generation-truncated");
        EvaluationTrace trace = trace(result, "arch-vpc-three-tier");

        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.FAIL);
        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.GENERATION);
        assertThat(trace.firstDivergence().category())
                .isEqualTo(EvaluationFailureCategory.OUTPUT_TRUNCATED);
    }

    @Test
    void classifiesProviderNeutralGenerationResponseFormatFailure() {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());
        AnalysisGenerationStage invalidGenerator = (context, source, references) -> {
            throw new AnalysisGenerationResponseFormatException("invalid structured output");
        };

        EvaluationRunResult result = runner(retriever(), invalidGenerator)
                .run(dataset, "generation-format");
        EvaluationTrace trace = trace(result, "arch-vpc-three-tier");

        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.FAIL);
        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.GENERATION);
        assertThat(trace.firstDivergence().category())
                .isEqualTo(EvaluationFailureCategory.RESPONSE_FORMAT);
    }

    @Test
    void localizesClassificationMismatchAtGeneration() {
        LoadedEvaluationDataset dataset = new EvaluationDatasetLoader(objectMapper).load(datasetPath());
        AnalysisGenerationStage rejectingGenerator = (context, source, references) -> {
            throw new AnalysisInputRejectedException(
                    AnalysisInputClassification.NON_ARCHITECTURE_IMAGE,
                    0.99,
                    false,
                    null
            );
        };

        EvaluationRunResult result = runner(retriever(), rejectingGenerator).run(dataset, "classification-failure");
        EvaluationTrace trace = trace(result, "arch-cloudfront-private-alb");

        assertThat(trace.factExtraction().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(trace.retrieval().status()).isEqualTo(EvaluationStageStatus.PASS);
        assertThat(trace.generation().status()).isEqualTo(EvaluationStageStatus.FAIL);
        assertThat(trace.validation().status()).isEqualTo(EvaluationStageStatus.NOT_RUN);
        assertThat(trace.firstDivergence().stage()).isEqualTo(EvaluationStage.GENERATION);
        assertThat(trace.firstDivergence().category()).isEqualTo(EvaluationFailureCategory.INPUT_CLASSIFICATION);
    }

    private EvaluationRunner runner(ReferenceRetriever retriever, AnalysisGenerationStage generator) {
        ArchitectureFactsExtractor facts = source -> facts(source.metadata().key());
        return runner(facts, retriever, generator);
    }

    private EvaluationRunner runner(
            ArchitectureFactsExtractor facts,
            ReferenceRetriever retriever,
            AnalysisGenerationStage generator
    ) {
        ConfigurationIdentity configuration = new ConfigurationIdentity(
                "terraformers-reference-v2",
                "5.100.0",
                "bedrock",
                "bedrock",
                RetrievalMode.REQUIRED.name(),
                5,
                "stub-generation-model",
                "stub-embedding-model",
                "stub-config-sha256"
        );
        return new EvaluationRunner(
                facts,
                new RetrievalQueryTextBuilder(),
                retriever,
                generator,
                new TerraformDraftValidator(),
                RetrievalMode.REQUIRED,
                configuration
        );
    }

    private ReferenceRetriever retriever() {
        return query -> List.of(new ReferenceDocument(
                "stub-reference",
                "Stub reference",
                "Repository-owned evaluation reference",
                0.9,
                "PROJECT_DECISION",
                query.resourceTypes(),
                "evaluation/stub",
                "5.100.0",
                "terraformers-reference-v2",
                "PROJECT_DECISION",
                100,
                List.of()
        ));
    }

    private AnalysisGenerationStage generator() {
        return (context, source, references) -> {
            String caseId = source.metadata().key();
            if (caseId.equals("ambiguous-cropped-service-sketch")) {
                throw new AnalysisInputRejectedException(
                        AnalysisInputClassification.AMBIGUOUS,
                        0.70,
                        false,
                        null
                );
            }
            if (caseId.equals("non-architecture-deployment-dashboard")) {
                throw new AnalysisInputRejectedException(
                        AnalysisInputClassification.NON_ARCHITECTURE_IMAGE,
                        0.99,
                        false,
                        null
                );
            }
            return new AnalysisGenerationResult(
                    "stub",
                    AnalysisInputClassification.ARCHITECTURE_DIAGRAM,
                    0.95,
                    "resource \"aws_vpc\" \"main\" { cidr_block = \"10.0.0.0/16\" }",
                    "stub architecture",
                    List.of("VPC"),
                    List.of("VPC contains workload"),
                    List.of(),
                    "end_turn",
                    100,
                    false
            );
        };
    }

    private ArchitectureRetrievalFacts facts(String caseId) {
        return switch (caseId) {
            case "arch-vpc-three-tier" -> new ArchitectureRetrievalFacts(
                    "VPC three tier", List.of("ALB", "Application", "RDS"),
                    List.of("ALB -> Application", "Application -> RDS"),
                    List.of("aws_vpc", "aws_lb", "aws_db_instance"));
            case "arch-cloudfront-private-alb" -> new ArchitectureRetrievalFacts(
                    "CloudFront private ALB", List.of("CloudFront", "Private ALB", "EKS"),
                    List.of("CloudFront -> Private ALB", "Private ALB -> EKS"),
                    List.of("aws_cloudfront_distribution", "aws_lb", "aws_eks_cluster"));
            case "arch-private-aoss" -> new ArchitectureRetrievalFacts(
                    "Private OpenSearch Serverless",
                    List.of("Backend Reader", "VPC Endpoint", "OpenSearch Serverless"),
                    List.of("Backend Reader -> VPC Endpoint", "VPC Endpoint -> OpenSearch Serverless"),
                    List.of("aws_opensearchserverless_collection", "aws_opensearchserverless_vpc_endpoint"));
            case "arch-s3-metadata-split" -> new ArchitectureRetrievalFacts(
                    "S3 content and relational metadata", List.of("Spring Boot API", "MariaDB", "S3"),
                    List.of("API -> MariaDB", "API -> S3"),
                    List.of("aws_s3_bucket", "aws_db_instance"));
            case "ambiguous-cropped-service-sketch" -> new ArchitectureRetrievalFacts(
                    "cropped API cache sketch", List.of("API", "Cache"), List.of(), List.of());
            case "non-architecture-deployment-dashboard" -> new ArchitectureRetrievalFacts(
                    "deployment dashboard labels", List.of("deployment"), List.of(), List.of());
            default -> throw new IllegalArgumentException("unexpected evaluation case " + caseId);
        };
    }

    private EvaluationTrace trace(EvaluationRunResult result, String caseId) {
        return result.traces().stream()
                .filter(trace -> trace.caseId().equals(caseId))
                .findFirst()
                .orElseThrow();
    }

    private Path datasetPath() {
        return Path.of("..", "evaluation", "terraformers-eval-v1", "dataset.json");
    }
}
