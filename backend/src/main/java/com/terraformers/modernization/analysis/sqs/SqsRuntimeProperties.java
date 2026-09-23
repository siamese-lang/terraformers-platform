package com.terraformers.modernization.analysis.sqs;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "terraformers.analysis.sqs")
public class SqsRuntimeProperties {
    private String progressQueueUrl;
    private String resultQueueUrl;

    public String getProgressQueueUrl() { return progressQueueUrl; }
    public void setProgressQueueUrl(String progressQueueUrl) { this.progressQueueUrl = progressQueueUrl; }
    public String getResultQueueUrl() { return resultQueueUrl; }
    public void setResultQueueUrl(String resultQueueUrl) { this.resultQueueUrl = resultQueueUrl; }
}
