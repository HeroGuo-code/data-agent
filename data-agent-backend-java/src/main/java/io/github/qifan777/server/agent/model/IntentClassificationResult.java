package io.github.qifan777.server.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class IntentClassificationResult {
    @JsonPropertyDescription("意图分类：仅允许《闲聊或无关指令》或《可能的数据分析请求》")
    @JsonProperty("classification")
    private String classification = "";

    public String getClassification() { return classification; }
    public void setClassification(String v) { this.classification = v; }
}
