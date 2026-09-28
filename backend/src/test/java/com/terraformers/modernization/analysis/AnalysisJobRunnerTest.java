package com.terraformers.modernization.analysis;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.terraformers.modernization.storage.ObjectWriteResult;
import java.util.List;
import java.util.Optional;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AnalysisJobRunnerTest {

    @Test
    void committedPendingJobRunsOutsideCreateRequestAndReachesTerminalStatus() {
        AnalysisJobOrchestrator orchestrator = mock(AnalysisJobOrchestrator.class);
        AnalysisJobStateService stateService = mock(AnalysisJobStateService.class);
        AnalysisJobEntity running = new AnalysisJobEntity();
        running.setProjectId(42L);
        running.setSourceFileId(101L);
        running.setSourceBucket("source-bucket");
        running.setSourceKey("source/key.png");
        running.prePersist();
        AnalysisJobExecution execution = new AnalysisJobExecution(
                new AnalysisResult(
                        "stub",
                        "resource \"aws_s3_bucket\" \"accepted\" {}",
                        "summary",
                        List.of("S3"),
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new ObjectWriteResult("s3", true, "result-bucket", "analysis/main.tf", "etag")
        );
        when(stateService.claimPending("job-1")).thenReturn(Optional.of(running));
        when(orchestrator.executeProviderAndStoreDraft(running)).thenReturn(execution);
        AnalysisJobRunner runner = new AnalysisJobRunner(orchestrator, stateService, new AnalysisObservability(new SimpleMeterRegistry()));

        runner.run("job-1");

        InOrder inOrder = inOrder(stateService, orchestrator);
        inOrder.verify(stateService).claimPending("job-1");
        inOrder.verify(orchestrator).executeProviderAndStoreDraft(running);
        inOrder.verify(stateService).markSucceeded("job-1", execution);
    }

    @Test
    void readTimeoutMarksJobFailedWithSafeTimeoutMessage() {
        AnalysisJobOrchestrator orchestrator = mock(AnalysisJobOrchestrator.class);
        AnalysisJobStateService stateService = mock(AnalysisJobStateService.class);
        AnalysisJobEntity running = new AnalysisJobEntity();
        when(stateService.claimPending("job-1")).thenReturn(Optional.of(running));
        when(orchestrator.executeProviderAndStoreDraft(running))
                .thenThrow(new AnalysisProviderTimeoutException(new SocketTimeoutException("Read timed out")));

        new AnalysisJobRunner(orchestrator, stateService, new AnalysisObservability(new SimpleMeterRegistry())).run("job-1");

        verify(stateService).markFailed("job-1", AnalysisJobRunner.TIMEOUT_FAILURE_REASON);
    }

    @Test
    void generalFailureDoesNotExposeInternalExceptionDetails() {
        AnalysisJobOrchestrator orchestrator = mock(AnalysisJobOrchestrator.class);
        AnalysisJobStateService stateService = mock(AnalysisJobStateService.class);
        AnalysisJobEntity running = new AnalysisJobEntity();
        when(stateService.claimPending("job-1")).thenReturn(Optional.of(running));
        when(orchestrator.executeProviderAndStoreDraft(running))
                .thenThrow(new IllegalStateException("secret request body and stack details"));

        new AnalysisJobRunner(orchestrator, stateService, new AnalysisObservability(new SimpleMeterRegistry())).run("job-1");

        verify(stateService).markFailed("job-1", AnalysisJobRunner.GENERIC_FAILURE_REASON);
    }

    @Test
    void apiCallAttemptTimeoutMarksJobFailedWithSafeTimeoutMessage() {
        assertFailureReason(new AnalysisProviderTimeoutException(new RuntimeException("attempt timed out")),
                AnalysisJobRunner.TIMEOUT_FAILURE_REASON);
    }

    @Test
    void apiCallTimeoutMarksJobFailedWithSafeTimeoutMessage() {
        assertFailureReason(new AnalysisProviderTimeoutException(new RuntimeException("call timed out")),
                AnalysisJobRunner.TIMEOUT_FAILURE_REASON);
    }

    @Test
    void generalProviderExceptionMarksJobFailedWithGenericMessage() {
        assertFailureReason(new IllegalStateException("connection reset"),
                AnalysisJobRunner.GENERIC_FAILURE_REASON);
    }

    @Test
    void truncatedBedrockOutputMarksJobFailedWithSafeMessage() {
        assertFailureReason(providerFailure(AnalysisProviderFailureReason.OUTPUT_TRUNCATED),
                AnalysisJobRunner.TRUNCATED_FAILURE_REASON);
    }

    @Test
    void invalidBedrockFormatMarksJobFailedWithSafeMessage() {
        assertFailureReason(providerFailure(AnalysisProviderFailureReason.RESPONSE_FORMAT),
                AnalysisJobRunner.FORMAT_FAILURE_REASON);
    }

    @Test
    void rejectedArchitectureInputMarksJobFailedWithDedicatedMessage() {
        assertFailureReason(providerFailure(AnalysisProviderFailureReason.INPUT_REJECTED),
                AnalysisJobRunner.REJECTED_INPUT_FAILURE_REASON);
    }

    private AnalysisProviderFailureException providerFailure(AnalysisProviderFailureReason reason) {
        return new AnalysisProviderFailureException(reason, new RuntimeException("provider detail"));
    }

    private void assertFailureReason(RuntimeException exception, String expectedReason) {
        AnalysisJobOrchestrator orchestrator = mock(AnalysisJobOrchestrator.class);
        AnalysisJobStateService stateService = mock(AnalysisJobStateService.class);
        AnalysisJobEntity running = new AnalysisJobEntity();
        when(stateService.claimPending("job-1")).thenReturn(Optional.of(running));
        when(orchestrator.executeProviderAndStoreDraft(running)).thenThrow(exception);

        new AnalysisJobRunner(orchestrator, stateService, new AnalysisObservability(new SimpleMeterRegistry())).run("job-1");

        verify(stateService).markFailed("job-1", expectedReason);
    }
    @Test
    void claimFailureIsNotReclassifiedAsJobFailure() {
        AnalysisJobOrchestrator orchestrator = mock(AnalysisJobOrchestrator.class);
        AnalysisJobStateService stateService = mock(AnalysisJobStateService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("transition failed")).when(stateService).claimPending("job-transition");

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new AnalysisJobRunner(orchestrator, stateService, new AnalysisObservability(new SimpleMeterRegistry())).run("job-transition"))
                .isInstanceOf(IllegalStateException.class);
        verify(stateService, org.mockito.Mockito.never()).markFailed(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void nonPendingJobIsSkippedWithoutStartingOrchestration() {
        AnalysisJobOrchestrator orchestrator = mock(AnalysisJobOrchestrator.class);
        AnalysisJobStateService stateService = mock(AnalysisJobStateService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        when(stateService.claimPending("job-terminal")).thenReturn(Optional.empty());

        new AnalysisJobRunner(orchestrator, stateService, new AnalysisObservability(registry)).run("job-terminal");

        verify(orchestrator, org.mockito.Mockito.never())
                .executeProviderAndStoreDraft(org.mockito.ArgumentMatchers.any());
        org.assertj.core.api.Assertions.assertThat(
                registry.find("terraformers.analysis.jobs").tags("outcome", "started").counter()
        ).isNull();
        org.assertj.core.api.Assertions.assertThat(registry.find("terraformers.analysis.claims")
                .tags("outcome", "not_claimed").counter().count()).isEqualTo(1);
    }

    @Test
    void beforeStateTransientProviderFailureIsNotRetriedAndJobBecomesTerminal() {
        AnalysisJobOrchestrator orchestrator = mock(AnalysisJobOrchestrator.class);
        AnalysisJobStateService stateService = mock(AnalysisJobStateService.class);
        AnalysisJobEntity running = new AnalysisJobEntity();
        running.prePersist();
        AtomicInteger invocations = new AtomicInteger();
        when(stateService.claimPending("job-transient")).thenReturn(Optional.of(running));
        when(orchestrator.executeProviderAndStoreDraft(running)).thenAnswer(ignored -> {
            if (invocations.incrementAndGet() == 1) {
                throw new AnalysisProviderTimeoutException(new SocketTimeoutException("temporary timeout"));
            }
            return mock(AnalysisJobExecution.class);
        });
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new AnalysisJobRunner(orchestrator, stateService, new AnalysisObservability(registry)).run("job-transient");

        org.assertj.core.api.Assertions.assertThat(invocations).hasValue(1);
        verify(stateService).markFailed("job-transient", AnalysisJobRunner.TIMEOUT_FAILURE_REASON);
        org.assertj.core.api.Assertions.assertThat(registry.find("terraformers.analysis.claims")
                .tags("outcome", "claimed").counter().count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(registry.find("terraformers.analysis.queue.wait")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void orchestrationFailureAfterRunningRecordsStartedAndFailedMetrics() {
        AnalysisJobOrchestrator orchestrator = mock(AnalysisJobOrchestrator.class);
        AnalysisJobStateService stateService = mock(AnalysisJobStateService.class);
        AnalysisJobEntity running = new AnalysisJobEntity();
        when(stateService.claimPending("job-observed")).thenReturn(Optional.of(running));
        when(orchestrator.executeProviderAndStoreDraft(running)).thenThrow(new IllegalStateException("sensitive detail"));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new AnalysisJobRunner(orchestrator, stateService, new AnalysisObservability(registry)).run("job-observed");

        verify(stateService).markFailed(org.mockito.ArgumentMatchers.eq("job-observed"), org.mockito.ArgumentMatchers.anyString());
        org.assertj.core.api.Assertions.assertThat(registry.find("terraformers.analysis.jobs").tags("outcome", "started").counter().count()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(registry.find("terraformers.analysis.jobs").tags("outcome", "failed").counter().count()).isEqualTo(1);
    }

}
