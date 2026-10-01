package com.terraformers.modernization.evaluation.adaptive;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.terraformers.modernization.analysis.AnalysisRuntimeProperties;
import com.terraformers.modernization.analysis.vertex.VertexRuntimeProperties;
import com.terraformers.modernization.reference.RetrievalQueryTextBuilder;
import com.terraformers.modernization.reference.VertexEmbeddingProvider;
import com.terraformers.modernization.reference.opensearch.HttpOpenSearchTransport;
import com.terraformers.modernization.reference.opensearch.OpenSearchKnnQueryBuilder;
import com.terraformers.modernization.reference.opensearch.OpenSearchResponseParser;
import java.nio.file.Files;
import java.nio.file.Path;

public final class AdaptiveRetrievalProbeLauncher {
    private AdaptiveRetrievalProbeLauncher() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            throw new IllegalArgumentException("configure the adaptive retrieval probe with environment variables");
        }

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Path fixturePath = Path.of(required("ADAPTIVE_RETRIEVAL_FIXTURE"));
        Path outputPath = Path.of(required("ADAPTIVE_RETRIEVAL_OUTPUT"));
        AdaptiveRetrievalProbeFixture fixture = AdaptiveRetrievalProbeFixture.load(mapper, fixturePath);

        VertexRuntimeProperties vertex = new VertexRuntimeProperties();
        vertex.setProjectId(required("GOOGLE_CLOUD_PROJECT"));
        vertex.setLocation(required("GOOGLE_CLOUD_LOCATION"));
        vertex.setEmbeddingModelId(required("VERTEX_EMBEDDING_MODEL_ID"));
        vertex.setEmbeddingDimension(integer("EXPECTED_VECTOR_DIMENSION"));

        AnalysisRuntimeProperties analysis = new AnalysisRuntimeProperties();
        analysis.setOpensearchEndpoint(required("OPENSEARCH_ENDPOINT"));
        analysis.setIndexName(required("INDEX_NAME"));
        analysis.setVectorFieldName(required("VECTOR_FIELD_NAME"));
        analysis.setContentFieldName(required("CONTENT_FIELD_NAME"));
        analysis.setCorpusVersion(required("CORPUS_VERSION"));
        analysis.setProviderVersion(required("PROVIDER_VERSION"));
        analysis.setExpectedVectorDimension(integer("EXPECTED_VECTOR_DIMENSION"));
        analysis.setOpensearchTopK(AdaptiveRetrievalProbeRunner.BASE_TOP_K);
        analysis.setOpensearchMaxEvidence(AdaptiveRetrievalProbeRunner.ADAPTIVE_MAX_EVIDENCE);

        if (analysis.getExpectedVectorDimension() != 1024) {
            throw new IllegalArgumentException("EXPECTED_VECTOR_DIMENSION must be 1024");
        }

        Client client = Client.builder()
                .project(vertex.requireProjectId())
                .location(vertex.requireLocation())
                .vertexAI(true)
                .httpOptions(HttpOptions.builder().apiVersion("v1").build())
                .build();

        AdaptiveRetrievalProbeRunner runner = new AdaptiveRetrievalProbeRunner(
                new VertexEmbeddingProvider(client, vertex),
                new OpenSearchKnnQueryBuilder(mapper),
                new OpenSearchResponseParser(mapper),
                new HttpOpenSearchTransport(),
                analysis,
                new RetrievalQueryTextBuilder()
        );

        AdaptiveRetrievalProbeReport report = runner.run(
                fixture,
                required("SOURCE_COMMIT"),
                vertex.requireEmbeddingModelId(),
                vertex.requireEmbeddingDimension()
        );
        Files.createDirectories(outputPath.toAbsolutePath().getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), report);
        System.out.printf(
                "Adaptive retrieval probe ready scenario=%s controlCoverage=%d/%d adaptiveCoverage=%d/%d output=%s%n",
                report.scenarioId(),
                report.control().coverageMatched(), report.control().coverageTotal(),
                report.adaptive().coverageMatched(), report.adaptive().coverageTotal(),
                outputPath
        );
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.strip();
    }

    private static int integer(String name) {
        return Integer.parseInt(required(name));
    }
}
