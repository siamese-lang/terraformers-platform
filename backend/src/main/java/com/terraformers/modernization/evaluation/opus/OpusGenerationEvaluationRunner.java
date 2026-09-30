package com.terraformers.modernization.evaluation.opus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.terraformers.modernization.analysis.*;
import com.terraformers.modernization.analysis.vertex.*;
import com.terraformers.modernization.evaluation.EvaluationCase;
import com.terraformers.modernization.storage.ObjectContent;
import com.terraformers.modernization.storage.ObjectMetadata;
import java.util.*;
import java.util.regex.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class OpusGenerationEvaluationRunner {
    public static final String MODEL="claude-opus-5-5", LOCATION="global", PUBLISHER="anthropic";
    private static final Pattern RESOURCE=Pattern.compile("(?m)^\\s*resource\\s+\"([^\"]+)\"\\s+\"");
    private final ObjectMapper mapper; private final ClaudeVertexClient client; private final VertexPromptBuilder prompts;
    private final VertexResponseParser parser; private final TerraformDraftValidator validator; private final int maxTokens;
    public OpusGenerationEvaluationRunner(ObjectMapper m,ClaudeVertexClient c,int max){mapper=m;client=c;maxTokens=max;prompts=new VertexPromptBuilder();parser=new VertexResponseParser(m);validator=new TerraformDraftValidator();}

    public CaseEvidence evaluate(OpusGenerationFixtureLoader.FixtureCase fixture) {
        long start=System.nanoTime();
        ObjectNode standardRequest=request(fixture,false);
        String promptSha256=promptSha256(standardRequest);
        String compactPromptSha256=null;
        ClaudeVertexClient.ClaudeResponse response;
        boolean retry=false;
        try {
            response=client.rawPredict(standardRequest);
            if ("max_tokens".equals(response.stopReason())) {
                retry=true;
                ObjectNode compactRequest=request(fixture,true);
                compactPromptSha256=promptSha256(compactRequest);
                response=client.rawPredict(compactRequest);
            }
        } catch (GoogleClaudeVertexClient.ClaudeProviderException exception) {
            return providerFailure(fixture,(System.nanoTime()-start)/1_000_000,retry,
                    exception.category(),exception.getMessage(),exception.httpStatus(),promptSha256,compactPromptSha256);
        }
        long latency=(System.nanoTime()-start)/1_000_000;
        if ("max_tokens".equals(response.stopReason())) return failure(fixture,response,latency,true,"OUTPUT_TRUNCATED","second response reached max_tokens",promptSha256,compactPromptSha256);
        JsonNode raw;
        try { raw=mapper.readTree(response.text()); } catch(Exception e){return failure(fixture,response,latency,retry,"RESPONSE_FORMAT","malformed structured response",promptSha256,compactPromptSha256);}
        String rawTerraform=raw.path("terraformCode").isTextual()?raw.path("terraformCode").asText():"";
        AnalysisInputClassification observed=null; Double confidence=raw.path("classificationConfidence").isNumber()?raw.path("classificationConfidence").doubleValue():null;
        try { observed=AnalysisInputClassification.valueOf(raw.path("inputType").asText()); }catch(Exception ignored){}
        AnalysisGenerationResult generation=null; boolean format=true;
        try { generation=parser.parse("vertex:"+MODEL,response.text(),response.stopReason(),response.outputTokens(),retry); }
        catch(AnalysisInputRejectedException e){ observed=e.classification(); confidence=e.classificationConfidence(); }
        catch(RuntimeException e){ format=false; }
        Set<String> resources=extract(rawTerraform); EvaluationCase.TextExpectation expectation=fixture.definition().generation().terraformResourceTypes();
        List<String> matched=expectation.required().stream().filter(resources::contains).toList();
        List<String> missing=expectation.required().stream().filter(v->!resources.contains(v)).toList();
        List<String> forbidden=expectation.forbidden().stream().filter(resources::contains).toList();
        TerraformDraftValidation validation=fixture.definition().generation().terraformExpected()?validator.validate(rawTerraform):new TerraformDraftValidation(true,"",null);
        String category="NONE";
        if(!format) category="RESPONSE_FORMAT";
        else if(observed==null||!observed.name().equals(fixture.definition().expectedClassification().name())) category="INPUT_CLASSIFICATION";
        else if(!missing.isEmpty()) category="GENERATION_REQUIRED_RESOURCE_MISSING";
        else if(!forbidden.isEmpty()) category="GENERATION_FORBIDDEN_RESOURCE";
        else if(fixture.definition().generation().terraformExpected()&&!validation.valid()) category="TERRAFORM_STRUCTURAL_VALIDATION";
        return new CaseEvidence(fixture.definition().caseId(),fixture.definition().expectedClassification().name(),observed==null?null:observed.name(),confidence,fixture.referenceIds(),rawTerraform,List.copyOf(resources),matched,missing,forbidden,validation.valid(),validation.reason(),validation.reason()!=null&&validation.reason().contains("placeholder/example"),!rawTerraform.isBlank(),format,latency,response.inputTokens(),response.outputTokens(),response.stopReason(),retry,category,null,null,response.model(),generation==null?null:generation.summary(),promptSha256,compactPromptSha256);
    }

    public ObjectNode request(OpusGenerationFixtureLoader.FixtureCase fixture, boolean compact) {
        ObjectContent source=new ObjectContent(new ObjectMetadata("fixture",fixture.definition().input().path(),fixture.definition().input().contentType(),fixture.imageBytes().length,""),fixture.imageBytes());
        ObjectNode root=mapper.createObjectNode(); root.put("anthropic_version","vertex-2023-10-16").put("max_tokens",maxTokens).put("temperature",compact?0.1:0.2).put("stream",false);
        var content=root.putArray("messages").addObject().put("role","user").putArray("content");
        var image=content.addObject().put("type","image").putObject("source"); image.put("type","base64").put("media_type",fixture.definition().input().contentType()).put("data",Base64.getEncoder().encodeToString(fixture.imageBytes()));
        content.addObject().put("type","text").put("text",prompts.build(source,fixture.references(),compact));
        JsonNode schema=mapper.valueToTree(prompts.responseJsonSchema()); removeUnsupportedBounds(schema);
        root.putObject("output_config").putObject("format").put("type","json_schema").set("schema",schema);
        return root;
    }
    private void removeUnsupportedBounds(JsonNode n){if(n.isObject()){((ObjectNode)n).remove(List.of("minimum","maximum"));n.forEach(this::removeUnsupportedBounds);}else if(n.isArray())n.forEach(this::removeUnsupportedBounds);}
    private Set<String> extract(String terraform){Set<String>s=new LinkedHashSet<>();Matcher m=RESOURCE.matcher(terraform==null?"":terraform);while(m.find())s.add(m.group(1));return s;}
    private String promptSha256(ObjectNode request) {
        String prompt=request.path("messages").get(0).path("content").get(1).path("text").asText();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable",exception); }
    }
    private CaseEvidence failure(OpusGenerationFixtureLoader.FixtureCase f,ClaudeVertexClient.ClaudeResponse r,long l,boolean retry,String category,String reason,String promptHash,String compactHash){return new CaseEvidence(f.definition().caseId(),f.definition().expectedClassification().name(),null,null,f.referenceIds(),"",List.of(),List.of(),List.of(),List.of(),false,reason,false,false,false,l,r.inputTokens(),r.outputTokens(),r.stopReason(),retry,category,reason,null,r.model(),null,promptHash,compactHash);}
    private CaseEvidence providerFailure(OpusGenerationFixtureLoader.FixtureCase f,long latency,boolean retry,String category,String reason,Integer status,String promptHash,String compactHash){return new CaseEvidence(f.definition().caseId(),f.definition().expectedClassification().name(),null,null,f.referenceIds(),"",List.of(),List.of(),List.of(),List.of(),false,null,false,false,false,latency,null,null,null,retry,category,reason,status,null,null,promptHash,compactHash);}

    public record CaseEvidence(String caseId,String expectedClassification,String observedClassification,Double classificationConfidence,List<String> suppliedReferenceIds,String generatedTerraform,List<String> generatedTerraformResourceTypes,List<String> requiredResourceTypesMatched,List<String> requiredResourceTypesMissing,List<String> forbiddenResourceTypesPresent,boolean terraformDraftValidatorPassed,String terraformDraftValidatorReason,boolean placeholderExampleDetected,boolean terraformEmitted,boolean responseFormatPassed,long latencyMs,Integer inputTokens,Integer outputTokens,String stopReason,boolean compactRetryOccurred,String firstFailureCategory,String failureReason,Integer httpStatus,String responseModel,String summary,String renderedPromptSha256,String compactPromptSha256){}
}
