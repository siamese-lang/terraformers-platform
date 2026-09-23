package com.terraformers.modernization.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.analysis.AnalysisGenerationStage;
import com.terraformers.modernization.analysis.AnalysisInputClassification;
import com.terraformers.modernization.analysis.AnalysisInputRejectedException;
import com.terraformers.modernization.analysis.TerraformDraftValidator;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader.LoadedEvaluationDataset;
import com.terraformers.modernization.evaluation.EvaluationTrace.ConfigurationIdentity;
import com.terraformers.modernization.reference.ArchitectureFactsExtractor;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.reference.ReferenceRetriever;
import com.terraformers.modernization.reference.RetrievalMode;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvaluationRunnerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
