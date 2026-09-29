package com.terraformers.modernization.evaluation;

import com.terraformers.modernization.evaluation.EvaluationCase.*;
import com.terraformers.modernization.evaluation.EvaluationTrace.*;
import java.util.List;

final class CaseATestFixtures {
    static ConfigurationIdentity identity() { return new ConfigurationIdentity("terraformers-reference-v3", "5.100.0", "vertex", "vertex", "REQUIRED", 8, "gemini-3.8-flash", "gemini-embedding-001", "legacy-hash", "LOW", 800, 8192); }
    static EvaluationCase definition(String id, InputClassification classification, List<String> decisions, List<String> retrievalResources) {
        boolean positive = classification == InputClassification.ARCHITECTURE_DIAGRAM;
        return new EvaluationCase("m3-evaluation-v1", "dataset", id,
                new InputFixture("fixtures/x.webp", "abc", "image/webp"), classification,
                TextExpectation.empty(), TextExpectation.empty(), TextExpectation.empty(),
                new RetrievalExpectation(List.of(), List.of(), retrievalResources, decisions, List.of()),
                new GenerationExpectation(positive, new TextExpectation(
                        List.of("aws_vpc", "aws_lb", "aws_db_instance", "aws_security_group"), List.of(), List.of("aws_s3_bucket"))),
                positive ? ValidationExpectation.PASS : ValidationExpectation.NOT_APPLICABLE, List.of());
    }
    static EvaluationTrace trace(String run, String id, EvaluationStageStatus retrievalStatus, List<ReferenceHit> hits,
                                 List<String> generated, boolean valid) {
        var facts = StageTrace.pass(EvaluationStage.FACT_EXTRACTION, 10,
                new FactExtractionEvidence(InputClassification.ARCHITECTURE_DIAGRAM, "facts", List.of(), List.of(), List.of()));
        StageTrace<RetrievalEvidence> retrieval;
        FirstDivergence divergence = null;
        if (retrievalStatus == EvaluationStageStatus.PASS) retrieval = StageTrace.pass(EvaluationStage.RETRIEVAL, 20, new RetrievalEvidence("query", List.of(), 8, hits));
        else if (retrievalStatus == EvaluationStageStatus.NOT_RUN) retrieval = StageTrace.notRun(EvaluationStage.RETRIEVAL);
        else { var failure = new EvaluationFailure(EvaluationStage.RETRIEVAL, EvaluationFailureCategory.RETRIEVAL_FAILURE, "failed"); retrieval = StageTrace.fail(EvaluationStage.RETRIEVAL, 20, null, failure); divergence = new FirstDivergence(failure.stage(), failure.category()); }
        StageTrace<GenerationEvidence> generation = retrievalStatus == EvaluationStageStatus.FAIL
                ? StageTrace.notRun(EvaluationStage.GENERATION)
                : StageTrace.pass(EvaluationStage.GENERATION, 30, new GenerationEvidence(List.of(),
                    InputClassification.ARCHITECTURE_DIAGRAM, 1.0, "generated", List.of(), List.of(), List.of(),
                    generated.isEmpty() ? "" : "resource", generated, List.of(), "STOP", null, false));
        StageTrace<ValidationEvidence> validation = generated.isEmpty() ? StageTrace.notRun(EvaluationStage.VALIDATION)
                : StageTrace.pass(EvaluationStage.VALIDATION, 5, new ValidationEvidence(new ValidationCheck("validator", valid, ""), List.of()));
        return new EvaluationTrace("m3-evaluation-v1", "dataset", run, id,
                new InputIdentity("fixtures/x.webp", "abc", "image/webp"), identity(), facts, retrieval, generation, validation, divergence);
    }
    static ReferenceHit hit(int rank, String id, String... resources) { return new ReferenceHit(rank, id, .9, id, "PROJECT_DECISION", "TYPE", "source", List.of(resources), "5.100.0", "terraformers-reference-v3", 100, List.of()); }
    private CaseATestFixtures() {}
}
