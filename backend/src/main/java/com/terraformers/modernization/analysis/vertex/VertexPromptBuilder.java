package com.terraformers.modernization.analysis.vertex;

import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class VertexPromptBuilder {

    public String build(ObjectContent source, List<ReferenceDocument> references, boolean compact) {
        requireSupportedImageMediaType(source.metadata().contentType());
        String referenceText = (references == null ? List.<ReferenceDocument>of() : references).stream()
                .map(this::formatReference)
                .collect(Collectors.joining("\n"));

        return """
                Analyze the image and return one JSON object matching the supplied response schema.

                Classification and output rules:
                - inputType must be exactly ARCHITECTURE_DIAGRAM, NON_ARCHITECTURE_IMAGE, or AMBIGUOUS.
                - ARCHITECTURE_DIAGRAM requires deployable system components and at least one identifiable connection, flow, dependency, containment, network boundary, or tier relationship.
                - Accept cloud, on-premises, WEB/WAS/DB, Kubernetes, API/message-flow, hand-drawn, and ordinary boxes-and-arrows architecture diagrams.
                - Classify photos, logos, isolated icons, memes, posters, banners, application/console UI screenshots, documents, tables, receipts, unrelated charts, and unconnected cloud-icon collections as NON_ARCHITECTURE_IMAGE.
                - Use AMBIGUOUS when system meaning or relationships cannot be determined, labels are insufficient, or the diagram is cropped.
                - For NON_ARCHITECTURE_IMAGE or AMBIGUOUS, summary/components/relationships/warnings/terraformCode must all be empty.
                - Only for ARCHITECTURE_DIAGRAM, terraformCode must contain raw Terraform HCL with real resource or module blocks.
                - Keep Terraform concise and limited to architecture visible in the input.
                - Do not include secrets, account IDs, access keys, static credentials, public S3 URLs, or real ARNs.
                - Treat PROJECT_DECISION references as mandatory project constraints when applicable.
                - Use PROVIDER_SCHEMA references for AWS Provider 5.100.0 argument and nested-block compatibility.
                - Provider examples demonstrate syntax only; do not copy settings marked by riskTags without adapting them to project constraints.

                %s

                Object metadata:
                - contentType: %s
                - contentLength: %s

                Retrieved reference evidence:
                %s
                """.formatted(
                compact
                        ? "Compact mode: minimize prose and Terraform while preserving only core components, relationships, and resources."
                        : "Standard mode: keep analysis and Terraform concise and avoid equivalent repeated detail.",
                source.metadata().contentType(),
                source.metadata().contentLength(),
                referenceText.isBlank() ? "- none" : referenceText
        );
    }

    public Map<String, Object> responseJsonSchema() {
        Map<String, Object> stringArray = Map.of("type", "array", "items", Map.of("type", "string"));
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "inputType", Map.of("type", "string", "enum",
                                List.of("ARCHITECTURE_DIAGRAM", "NON_ARCHITECTURE_IMAGE", "AMBIGUOUS")),
                        "classificationConfidence", Map.of("type", "number", "minimum", 0, "maximum", 1),
                        "classificationReason", Map.of("type", "string"),
                        "summary", Map.of("type", "string"),
                        "components", stringArray,
                        "relationships", stringArray,
                        "warnings", stringArray,
                        "terraformCode", Map.of("type", "string")
                ),
                "required", List.of(
                        "inputType", "classificationConfidence", "classificationReason", "summary",
                        "components", "relationships", "warnings", "terraformCode"
                ),
                "additionalProperties", false
        );
    }

    private String formatReference(ReferenceDocument reference) {
        String authority = blankTo(reference.authority(), "REFERENCE");
        String source = blankTo(reference.sourcePath(), reference.id());
        String risks = reference.riskTags().isEmpty() ? "none" : String.join(",", reference.riskTags());
        return "- id=%s; authority=%s; type=%s; source=%s; riskTags=%s; title=%s:\n%s".formatted(
                reference.id(),
                authority,
                blankTo(reference.documentType(), ""),
                source,
                risks,
                reference.title(),
                reference.content()
        );
    }

    private String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private void requireSupportedImageMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("source object content type is required for Vertex vision request");
        }
        String normalized = contentType.toLowerCase();
        if (!normalized.equals("image/png")
                && !normalized.equals("image/jpeg")
                && !normalized.equals("image/webp")
                && !normalized.equals("image/gif")) {
            throw new IllegalArgumentException("unsupported image content type for Vertex vision request: " + contentType);
        }
    }
}
