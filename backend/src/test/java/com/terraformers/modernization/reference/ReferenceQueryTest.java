package com.terraformers.modernization.reference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceQueryTest {

    @Test
    void normalizesCanonicalExplicitResourceTypes() {
        ReferenceQuery query = new ReferenceQuery(
                "architecture summary",
                List.of(" aws_vpc ", "aws_db_instance", "aws_vpc"),
                8
        );

        assertThat(query.resourceTypes()).containsExactly("aws_vpc", "aws_db_instance");
    }

    @Test
    void preservesAtMostSixteenDistinctResourceTypesForExplicitAndTextQueries() {
        List<String> resources = java.util.stream.IntStream.rangeClosed(1, 17)
                .mapToObj(index -> "aws_service_" + index)
                .toList();

        ReferenceQuery explicit = new ReferenceQuery("architecture summary", resources, 16);
        ReferenceQuery extracted = new ReferenceQuery(
                "architecture " + String.join(" ", resources), 16);

        assertThat(explicit.resourceTypes())
                .hasSize(ReferenceQuery.MAX_RESOURCE_TYPES)
                .containsExactlyElementsOf(resources.subList(0, ReferenceQuery.MAX_RESOURCE_TYPES));
        assertThat(extracted.resourceTypes())
                .containsExactlyElementsOf(explicit.resourceTypes());
        assertThat(explicit.resourceTypes()).doesNotContain("aws_service_17");
    }

    @Test
    void rejectsInvalidExplicitResourceTypeInsteadOfSilentlyDroppingIt() {
        assertThatThrownBy(() -> new ReferenceQuery(
                "architecture summary", List.of("AWS::EC2::VPC"), 8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Terraform AWS provider identifier")
                .hasMessageContaining("AWS::EC2::VPC");
    }

    @Test
    void acceptsAnExplicitlyEmptyResourceTypeList() {
        ReferenceQuery query = new ReferenceQuery("architecture summary", List.of(), 8);

        assertThat(query.resourceTypes()).isEmpty();
    }

    @Test
    void preservesTextExtractionConstructorCompatibility() {
        ReferenceQuery query = new ReferenceQuery(
                "architecture with aws_vpc and aws_db_instance", 8);

        assertThat(query.resourceTypes()).containsExactly("aws_vpc", "aws_db_instance");
    }
}
