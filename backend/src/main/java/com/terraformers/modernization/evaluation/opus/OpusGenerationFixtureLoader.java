package com.terraformers.modernization.evaluation.opus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.terraformers.modernization.evaluation.EvaluationCase;
import com.terraformers.modernization.evaluation.EvaluationDatasetLoader;
import com.terraformers.modernization.reference.ReferenceDocument;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

public final class OpusGenerationFixtureLoader {
    private final ObjectMapper mapper;
    public OpusGenerationFixtureLoader(ObjectMapper mapper) { this.mapper = mapper; }

    public LoadedFixture load(Path manifestFile, Path datasetFile, Path corpusFile) {
        try {
            byte[] manifestBytes = Files.readAllBytes(manifestFile);
            JsonNode manifest = mapper.readTree(manifestBytes);
            require(manifest.path("schemaVersion").asText().equals("opus-generation-fixture-v1"), "invalid Opus fixture schema");
            require(manifest.path("datasetVersion").asText().equals("terraformers-eval-v1"), "invalid dataset identity");
            require(manifest.path("corpusVersion").asText().equals("terraformers-reference-v3"), "invalid corpus identity");
            var loadedDataset = new EvaluationDatasetLoader(mapper).load(datasetFile);
            Map<String, EvaluationDatasetLoader.LoadedEvaluationCase> cases = new HashMap<>();
            loadedDataset.cases().forEach(c -> cases.put(c.definition().caseId(), c));
            Map<String, ReferenceDocument> documents = loadCorpus(corpusFile);
            List<FixtureCase> result = new ArrayList<>(); Set<String> ids = new HashSet<>();
            for (JsonNode item : manifest.path("cases")) {
                String id = item.path("caseId").asText(); require(ids.add(id), "duplicate manifest case: " + id);
                var datasetCase = cases.get(id); require(datasetCase != null, "unknown dataset case: " + id);
                EvaluationCase definition = datasetCase.definition();
                require(item.path("expectedClassification").asText().equals(definition.expectedClassification().name()), "classification mismatch: " + id);
                require(item.path("inputPath").asText().equals(definition.input().path()), "fixture path mismatch: " + id);
                require(item.path("inputSha256").asText().equals(definition.input().sha256()), "fixture SHA mismatch: " + id);
                List<String> referenceIds = new ArrayList<>(); List<ReferenceDocument> refs = new ArrayList<>();
                for (JsonNode ref : item.path("orderedReferenceIds")) { String refId=ref.asText(); referenceIds.add(refId); require(documents.containsKey(refId), "missing corpus document: " + refId); refs.add(documents.get(refId)); }
                result.add(new FixtureCase(definition, datasetCase.inputBytes(), List.copyOf(referenceIds), List.copyOf(refs)));
            }
            require(result.size() == 6, "Opus fixture must contain six cases");
            return new LoadedFixture(hex(MessageDigest.getInstance("SHA-256").digest(manifestBytes)), loadedDataset.dataset().datasetVersion(), List.copyOf(result));
        } catch (IOException | java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("failed to load Opus fixture", e); }
    }

    private Map<String, ReferenceDocument> loadCorpus(Path path) throws IOException {
        Map<String, ReferenceDocument> result = new HashMap<>();
        for (String line : Files.readAllLines(path)) { JsonNode n=mapper.readTree(line); String id=n.path("documentId").asText();
            require(!id.isBlank(), "corpus documentId is required"); require(!result.containsKey(id), "duplicate corpus document ID: " + id);
            require(n.path("corpusVersion").asText().equals("terraformers-reference-v3"), "invalid corpus document version: " + id);
            require(n.path("providerVersion").asText().equals("5.100.0"), "invalid provider version: " + id);
            result.put(id, new ReferenceDocument(id,n.path("title").asText(),n.path("content").asText(),0,n.path("documentType").asText(),strings(n.path("resourceTypes")),n.path("sourcePath").asText(),n.path("providerVersion").asText(),n.path("corpusVersion").asText(),n.path("authority").asText(),n.path("priority").asInt(),strings(n.path("riskTags")))); }
        return result;
    }
    private static List<String> strings(JsonNode n) { List<String> out=new ArrayList<>(); n.forEach(v->out.add(v.asText())); return List.copyOf(out); }
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
    private static String hex(byte[] bytes){return HexFormat.of().formatHex(bytes);}
    public record LoadedFixture(String manifestSha256,String datasetVersion,List<FixtureCase> cases){}
    public record FixtureCase(EvaluationCase definition,byte[] imageBytes,List<String> referenceIds,List<ReferenceDocument> references){ public byte[] imageBytes(){return imageBytes.clone();} }
}
