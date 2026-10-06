package com.terraformers.modernization.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.analysis.GeneratedTerraformContractInspector;
import com.terraformers.modernization.analysis.TerraformCliValidator;
import com.terraformers.modernization.analysis.TerraformDraftValidation;
import com.terraformers.modernization.analysis.TerraformDraftValidator;
import com.terraformers.modernization.evaluation.EvaluationTrace.FirstDivergence;
import com.terraformers.modernization.evaluation.EvaluationTrace.GenerationEvidence;
import com.terraformers.modernization.evaluation.EvaluationTrace.StageTrace;
import com.terraformers.modernization.evaluation.EvaluationTrace.ValidationCheck;
import com.terraformers.modernization.evaluation.EvaluationTrace.ValidationEvidence;
import com.terraformers.modernization.reference.AwsProviderSchemaCatalog;
import java.util.List;
import java.util.Objects;

/** Deterministically replays Terraform validation for an already-captured live evaluation artifact. */
public final class CaseAArtifactRescorer {
    private final TerraformDraftValidator draftValidator;
    private final GeneratedTerraformContractInspector contractInspector;
    private final TerraformCliValidator cliValidator;

    public CaseAArtifactRescorer(ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper");
        AwsProviderSchemaCatalog schemaCatalog = new AwsProviderSchemaCatalog(objectMapper);
        this.draftValidator = new TerraformDraftValidator();
        this.contractInspector = new GeneratedTerraformContractInspector(schemaCatalog);
        this.cliValidator = new TerraformCliValidator(objectMapper);
    }

    public EvaluationRunResult rescore(EvaluationRunResult run) {
        Objects.requireNonNull(run, "run");
        List<EvaluationTrace> rescored = run.traces().stream().map(this::rescoreTrace).toList();
        return new EvaluationRunResult(
                run.schemaVersion(),
                run.datasetVersion(),
                run.runId(),
                run.configuration(),
                rescored
        );
    }

    private EvaluationTrace rescoreTrace(EvaluationTrace trace) {
        GenerationEvidence generation = trace.generation().evidence();
        if (generation == null || generation.terraformCode().isBlank()) {
            return trace;
        }

        long startedAt = System.nanoTime();
        TerraformDraftValidation validation = LiveEvaluationLauncher.productionEquivalentValidation(
                generation.terraformCode(), draftValidator, contractInspector, cliValidator);
        long latencyMs = (System.nanoTime() - startedAt) / 1_000_000;

        ValidationEvidence evidence = new ValidationEvidence(
                new ValidationCheck(
                        "ProductionEquivalentTerraformValidatorReplay",
                        validation.valid(),
                        validation.reason(),
                        validation.diagnosticSummary()
                ),
                List.of()
        );

        StageTrace<ValidationEvidence> validationTrace;
        FirstDivergence divergence;
        if (validation.valid()) {
            validationTrace = StageTrace.pass(EvaluationStage.VALIDATION, latencyMs, evidence);
            divergence = earlierDivergence(trace);
        } else {
            EvaluationFailureCategory category = EvaluationRunner.validationFailureCategory(validation);
            validationTrace = StageTrace.fail(
                    EvaluationStage.VALIDATION,
                    latencyMs,
                    evidence,
                    new EvaluationFailure(EvaluationStage.VALIDATION, category, validation.reason())
            );
            divergence = earlierDivergence(trace);
            if (divergence == null) {
                divergence = new FirstDivergence(EvaluationStage.VALIDATION, category);
            }
        }

        return new EvaluationTrace(
                trace.schemaVersion(),
                trace.datasetVersion(),
                trace.runId(),
                trace.caseId(),
                trace.input(),
                trace.configuration(),
                trace.factExtraction(),
                trace.retrieval(),
                trace.generation(),
                validationTrace,
                divergence
        );
    }

    private FirstDivergence earlierDivergence(EvaluationTrace trace) {
        if (trace.factExtraction().status() == EvaluationStageStatus.FAIL
                || trace.retrieval().status() == EvaluationStageStatus.FAIL
                || trace.generation().status() == EvaluationStageStatus.FAIL) {
            return trace.firstDivergence();
        }
        return null;
    }
}
