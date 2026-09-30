package com.terraformers.modernization.evaluation.opus;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;

class OpusGenerationEvaluationTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final Path root=Path.of("..").toAbsolutePath().normalize();
    private OpusGenerationFixtureLoader.LoadedFixture fixture(){return new OpusGenerationFixtureLoader(mapper).load(root.resolve("evaluation/opus-generation-v1/manifest.json"),root.resolve("evaluation/terraformers-eval-v1/dataset.json"),root.resolve("corpus/terraformers-reference/v3/documents.jsonl"));}

    @Test void loadsSixUniqueCasesAndPreservesReferenceOrder(){var f=fixture();assertThat(f.cases()).hasSize(6);assertThat(f.cases()).extracting(c->c.definition().caseId()).doesNotHaveDuplicates();assertThat(f.cases().get(0).references()).extracting(r->r.id()).containsExactlyElementsOf(f.cases().get(0).referenceIds());}

    @Test void requestUsesFrozenClaudeContractAndSemanticPrompt(){var c=fixture().cases().get(0);var runner=new OpusGenerationEvaluationRunner(mapper,r->null,8192);var request=runner.request(c,false);assertThat(request.path("anthropic_version").asText()).isEqualTo("vertex-2023-10-16");assertThat(request.path("messages").get(0).path("content").get(0).path("source").path("media_type").asText()).isEqualTo("image/webp");assertThat(request.path("messages").get(0).path("content").get(1).path("text").asText()).contains("Classification and output rules:",c.referenceIds().get(0));assertThat(request.path("output_config").path("format").path("type").asText()).isEqualTo("json_schema");assertThat(request.toString()).doesNotContain("minimum","maximum","Authorization","Bearer");assertThat(request.path("output_config").path("format").path("schema").path("required")).hasSize(8);}

    @Test void parsesArchitectureAndScoresResources(){String text="{\"inputType\":\"ARCHITECTURE_DIAGRAM\",\"classificationConfidence\":0.9,\"classificationReason\":\"diagram\",\"summary\":\"system\",\"components\":[],\"relationships\":[],\"warnings\":[],\"terraformCode\":\"resource \\\"aws_vpc\\\" \\\"main\\\" { cidr_block = \\\"10.0.0.0/16\\\" }\\nresource \\\"aws_lb\\\" \\\"main\\\" { name = \\\"main\\\" }\"}";var c=fixture().cases().get(0);var evidence=new OpusGenerationEvaluationRunner(mapper,r->new ClaudeVertexClient.ClaudeResponse(text,"end_turn",10,20,"claude-opus-5-5"),8192).evaluate(c);assertThat(evidence.observedClassification()).isEqualTo("ARCHITECTURE_DIAGRAM");assertThat(evidence.requiredResourceTypesMatched()).contains("aws_vpc","aws_lb");assertThat(evidence.requiredResourceTypesMissing()).contains("aws_db_instance","aws_security_group");assertThat(evidence.firstFailureCategory()).isEqualTo("GENERATION_REQUIRED_RESOURCE_MISSING");}

    @Test void retriesTruncationOnlyOnce(){var responses=new ArrayDeque<>(List.of(new ClaudeVertexClient.ClaudeResponse("{}","max_tokens",1,8192,"claude-opus-5-5"),new ClaudeVertexClient.ClaudeResponse("{}","max_tokens",1,8192,"claude-opus-5-5")));var evidence=new OpusGenerationEvaluationRunner(mapper,r->responses.remove(),8192).evaluate(fixture().cases().get(0));assertThat(evidence.compactRetryOccurred()).isTrue();assertThat(evidence.firstFailureCategory()).isEqualTo("OUTPUT_TRUNCATED");assertThat(responses).isEmpty();}

    @Test void negativeTerraformIsExplicitAndResponseFormatFailure(){String text="{\"inputType\":\"AMBIGUOUS\",\"classificationConfidence\":0.7,\"classificationReason\":\"cropped\",\"summary\":\"\",\"components\":[],\"relationships\":[],\"warnings\":[],\"terraformCode\":\"resource \\\"aws_vpc\\\" \\\"bad\\\" { cidr_block = \\\"x\\\" }\"}";var c=fixture().cases().get(4);var e=new OpusGenerationEvaluationRunner(mapper,r->new ClaudeVertexClient.ClaudeResponse(text,"end_turn",1,2,"claude-opus-5-5"),8192).evaluate(c);assertThat(e.terraformEmitted()).isTrue();assertThat(e.responseFormatPassed()).isFalse();assertThat(e.firstFailureCategory()).isEqualTo("RESPONSE_FORMAT");}
}
