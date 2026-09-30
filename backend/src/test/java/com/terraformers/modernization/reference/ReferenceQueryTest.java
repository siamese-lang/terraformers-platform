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
