package com.terraformers.modernization.evaluation.opus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public final class OpusGenerationEvaluationLauncher {
    public static final String PROMPT_CONTRACT_SOURCE_COMMIT = "3fc610f1e9d601a4d5f79f281b358b783342c4ce";
    private OpusGenerationEvaluationLauncher() {}
    public static void main(String[] args) throws Exception {
        Map<String,String> e=System.getenv(); String location=e.getOrDefault("GOOGLE_CLOUD_LOCATION","global"); String model=e.getOrDefault("OPUS_MODEL_ID","claude-opus-5-5"); int max=Integer.parseInt(e.getOrDefault("OPUS_MAX_OUTPUT_TOKENS","8192"));
        if(!"global".equals(location)||!"claude-opus-5-5".equals(model))throw new IllegalArgumentException("O1 requires global claude-opus-5-5");
        ObjectMapper mapper=new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        Path manifest=requiredPath(e,"OPUS_FIXTURE_MANIFEST"),dataset=requiredPath(e,"EVALUATION_DATASET"),corpus=requiredPath(e,"CORPUS_DOCUMENTS"),output=requiredPath(e,"EVALUATION_OUTPUT");
        var fixture=new OpusGenerationFixtureLoader(mapper).load(manifest,dataset,corpus);
        var runner=new OpusGenerationEvaluationRunner(mapper,new GoogleClaudeVertexClient(required(e,"GOOGLE_CLOUD_PROJECT"),mapper),max);
        Artifact artifact=run(fixture,runner,required(e,"EVALUATION_RUN_ID"),required(e,"SOURCE_COMMIT"),model,location,max);
        Files.createDirectories(output.toAbsolutePath().getParent()); mapper.writeValue(output.toFile(),artifact);
    }
    static Artifact run(OpusGenerationFixtureLoader.LoadedFixture fixture,OpusGenerationEvaluationRunner runner,String runId,String sourceCommit,String model,String location,int max) {
        List<OpusGenerationEvaluationRunner.CaseEvidence> cases=new ArrayList<>();
        for(var c:fixture.cases())cases.add(runner.evaluate(c));
        long usable=cases.stream().filter(c->c.responseFormatPassed()&&!"OUTPUT_TRUNCATED".equals(c.firstFailureCategory())).count();
        return new Artifact("opus-generation-evaluation-v1",runId,sourceCommit,PROMPT_CONTRACT_SOURCE_COMMIT,fixture.manifestSha256(),fixture.datasetVersion(),"terraformers-reference-v3","5.100.0","anthropic",model,location,max,cases.size(),usable,Instant.now().toString(),List.copyOf(cases));
    }
    static String required(Map<String,String> e,String key){String v=e.get(key);if(v==null||v.isBlank())throw new IllegalArgumentException(key+" is required");return v;}
    static Path requiredPath(Map<String,String> e,String key){return Path.of(required(e,key));}
    public record Artifact(String schemaVersion,String runId,String sourceCommit,String promptContractSourceCommit,String fixtureManifestSha256,String datasetVersion,String corpusVersion,String providerVersion,String modelPublisher,String modelId,String location,int maxOutputTokens,int caseCount,long usableModelResponseCount,String createdAt,List<OpusGenerationEvaluationRunner.CaseEvidence> cases){}
}
