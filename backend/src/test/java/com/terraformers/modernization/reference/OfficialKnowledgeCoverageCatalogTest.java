package com.terraformers.modernization.reference;

import static org.assertj.core.api.Assertions.assertThat;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OfficialKnowledgeCoverageCatalogTest {
    private final OfficialKnowledgeCoverageCatalog coverage =
            new OfficialKnowledgeCoverageCatalog(new ObjectMapper());

    @Test
    void loadsVerifiedA7ZeroGapSetAndKeepsItIndependentOfRetrievalHits() {
        assertThat(coverage.gaps()).containsExactlyInAnyOrder(
                "aws_account_region", "aws_alb", "aws_alb_listener", "aws_alb_listener_certificate",
                "aws_alb_listener_rule", "aws_alb_target_group", "aws_alb_target_group_attachment",
                "aws_api_gateway_rest_api_put", "aws_ec2_image_block_public_access",
                "aws_pinpoint_email_template", "aws_rds_custom_db_engine_version",
                "aws_securityhub_configuration_policy_association");
        AwsProviderSchemaCatalog schema = new AwsProviderSchemaCatalog(new ObjectMapper(), Path.of(
                "src/test/resources/quality/aws-provider-schema-gap-fixture.json"));

        assertThat(coverage.gaps()).allSatisfy(type -> {
            assertThat(schema.contains(type)).isTrue();
            assertThat(coverage.isAvailable(type, schema)).isFalse();
        });
        assertThat(coverage.availableFor(Set.of("aws_vpc"), schema)).containsExactly("aws_vpc");
        assertThat(coverage.availableFor(Set.of(), schema)).isEmpty();
    }
}
