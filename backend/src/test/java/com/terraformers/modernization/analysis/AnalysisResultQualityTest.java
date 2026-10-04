package com.terraformers.modernization.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnalysisResultQualityTest {
    @Test
    void terraformSanitizationPreservesQualityAssessment() {
        var quality = TerminalQualityAssessmentMapper.failure(new IllegalStateException());
        var result = new AnalysisResult("provider", "unsafe", "summary", List.of(), List.of(), List.of(),
                List.of(), quality);
        assertThat(result.withTerraformCode("safe").qualityAssessment()).isSameAs(quality);
    }
}
