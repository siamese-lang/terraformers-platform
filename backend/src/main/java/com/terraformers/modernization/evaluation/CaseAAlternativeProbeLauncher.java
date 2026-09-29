package com.terraformers.modernization.evaluation;

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
import java.time.Duration;

/** Spring-free launcher for exactly one A3 six-snapshot comparison. */
public final class CaseAAlternativeProbeLauncher {
    private CaseAAlternativeProbeLauncher() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 0) throw new IllegalArgumentException("configure the A3 probe with environment variables");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Path fixturePath = Path.of(required("A3_FIXED_FACTS"));
        Path datasetPath = Path.of(required("EVALUATION_DATASET"));
        Path outputPath = Path.of(required("A3_OUTPUT"));
        CaseAAlternativeProbeFixture fixture = CaseAAlternativeProbeFixture.load(mapper, fixturePath);
        EvaluationDataset dataset = mapper.readValue(Files.readString(datasetPath), EvaluationDataset.class);

        VertexRuntimeProperties vertex = new VertexRuntimeProperties();
        vertex.setProjectId(required("GOOGLE_CLOUD_PROJECT"));
        vertex.setLocation(required("GOOGLE_CLOUD_LOCATION"));
        vertex.setEmbeddingModelId(required("VERTEX_EMBEDDING_MODEL_ID"));
        vertex.setEmbeddingDimension(integer("EXPECTED_VECTOR_DIMENSION"));
        AnalysisRuntimeProperties analysis = new AnalysisRuntimeProperties();
        analysis.setOpensearchEndpoint(required("OPENSEARCH_ENDPOINT"));
        analysis.setIndexName(required("INDEX_NAME")); analysis.setVectorFieldName(required("VECTOR_FIELD_NAME"));
        analysis.setContentFieldName(required("CONTENT_FIELD_NAME")); analysis.setCorpusVersion(required("CORPUS_VERSION"));
        analysis.setProviderVersion(required("PROVIDER_VERSION"));
        analysis.setExpectedVectorDimension(integer("EXPECTED_VECTOR_DIMENSION")); analysis.setOpensearchTopK(8);
        if (analysis.getExpectedVectorDimension() != 1024) throw new IllegalArgumentException("EXPECTED_VECTOR_DIMENSION must be 1024");

        Client client = Client.builder().project(vertex.requireProjectId()).location(vertex.requireLocation())
                .vertexAI(true).httpOptions(HttpOptions.builder().apiVersion("v1").build()).build();
        CaseAAlternativeProbeRunner runner = new CaseAAlternativeProbeRunner(new VertexEmbeddingProvider(client, vertex),
                new OpenSearchKnnQueryBuilder(mapper), new HttpOpenSearchTransport(), new OpenSearchResponseParser(mapper),
                analysis, new RetrievalQueryTextBuilder(), dataset.cases(),
                CaseAAlternativeProbeRunner.EmbeddingRequestPacer.paced(Duration.ofSeconds(13)));
        CaseAAlternativeProbeReport report = runner.run(fixture, required("SOURCE_COMMIT"),
                required("VERTEX_EMBEDDING_MODEL_ID"));
        Files.createDirectories(outputPath.toAbsolutePath().getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), report);
        System.out.printf("A3 retrieval probe completed snapshots=%d output=%s%n", report.snapshots().size(), outputPath);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }
    private static int integer(String name) { return Integer.parseInt(required(name)); }
}
