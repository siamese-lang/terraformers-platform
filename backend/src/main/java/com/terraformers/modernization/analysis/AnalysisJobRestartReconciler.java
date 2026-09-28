package com.terraformers.modernization.analysis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Closes non-terminal jobs left by the previous process before this single-replica runtime
 * becomes ready. This is intentionally not an automatic replay mechanism.
 */
@Component
public class AnalysisJobRestartReconciler implements ApplicationRunner {

    static final String INTERRUPTED_FAILURE_REASON =
            "서비스 재시작으로 분석이 중단되었습니다. 새 분석을 시작해 주세요.";

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobRestartReconciler.class);

    private final AnalysisJobStateService stateService;

    public AnalysisJobRestartReconciler(AnalysisJobStateService stateService) {
        this.stateService = stateService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int reconciled = stateService.reconcileInterrupted(INTERRUPTED_FAILURE_REASON);
        if (reconciled > 0) {
            log.warn("Reconciled interrupted analysis jobs outcome=failed count={}", reconciled);
        }
    }
}
