package com.terraformers.modernization.analysis.vertex;

import com.terraformers.modernization.reference.AwsProviderSchemaEvidence;
import com.terraformers.modernization.reference.ArchitectureRetrievalFacts;
import com.terraformers.modernization.analysis.AnalysisGenerationResult;
import com.terraformers.modernization.reference.ReferenceDocument;
import com.terraformers.modernization.storage.ObjectContent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class VertexPromptBuilder {

    public String build(ObjectContent source, List<ReferenceDocument> references, boolean compact) {
        return build(source, references, new AwsProviderSchemaEvidence(Map.of()), compact);
    }

    public String build(ObjectContent source, List<ReferenceDocument> references,
                        AwsProviderSchemaEvidence schemaEvidence, boolean compact) {
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
                - Only for ARCHITECTURE_DIAGRAM, terraformCode must contain raw Terraform HCL with AWS resource blocks only. Module blocks are forbidden.
                - Keep Terraform concise and limited to architecture visible in the input.
                - Treat PROJECT_DECISION references as mandatory project constraints when applicable.
                - Use the exact provider schema evidence below for AWS Provider 5.100.0 argument and nested-block compatibility.
                - Provider examples demonstrate syntax only; do not copy settings marked by riskTags without adapting them to project constraints.

                %s

                Object metadata:
                - contentType: %s
                - contentLength: %s

                Retrieved reference evidence:
                %s

                Request-relevant AWS Provider 5.100.0 schema context:
                %s
                """.formatted(
                compact
                        ? "Compact mode: minimize prose and Terraform while preserving only core components, relationships, and resources."
                        : "Standard mode: keep analysis and Terraform concise and avoid equivalent repeated detail.",
                source.metadata().contentType(),
                source.metadata().contentLength(),
                referenceText.isBlank() ? "- none" : referenceText,
                schemaEvidence.promptText()
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

    public String buildRepair(ArchitectureRetrievalFacts facts, AnalysisGenerationResult original,
            List<ReferenceDocument> references, AwsProviderSchemaEvidence schemaEvidence) {
        String evidence = references.stream().map(this::formatReference).collect(Collectors.joining("\n"));
        return """
                Correct only the prior Terraform draft. Return one JSON object containing terraformCode.
                This is a Terraform grounding repair, not a new image analysis or classification.
                - Preserve the original architecture intent, components, and relationships.
                - Correct HCL using supplied official provider documentation and exact AWS Provider 5.100.0 schema.
                - Do not introduce unrelated architecture components or new AWS resource types beyond those
                  supported by the supplied closure evidence and provider schema.
                - Module blocks are forbidden. Do not invent structurally invalid arguments or nested blocks.
                - Satisfy required provider arguments and preserve argument/nested-block compatibility.
                - For deployment-specific required values absent from the diagram/evidence, use editable
                  Terraform variable/reference placeholders rather than arbitrary concrete identifiers.
                - Preserve applicable PROJECT_DECISION constraints. Examples demonstrate syntax only;
                  adapt settings marked by riskTags to project constraints.
                - Return a corrected Terraform draft, not a fresh unrelated architecture interpretation.

                Original architecture facts: %s
                Original summary: %s
                Original components: %s
                Original relationships: %s

                Prior Terraform draft:
                %s

                Merged official and project reference evidence:
                %s

                Expanded exact AWS Provider 5.100.0 schema evidence:
                %s
                """.formatted(facts, original.summary(), original.components(), original.relationships(),
                original.terraformCode(), evidence.isBlank() ? "- none" : evidence, schemaEvidence.promptText());
    }

    public Map<String, Object> repairResponseJsonSchema() {
        return Map.of("type", "object", "properties", Map.of("terraformCode", Map.of("type", "string")),
                "required", List.of("terraformCode"), "additionalProperties", false);
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
