package io.github.qifan777.server.agent.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class EvidenceQueryRewriteResult {
    @JsonPropertyDescription("重写后的完整句子")
    @JsonProperty("standalone_query")
    private String standaloneQuery = "";

    public String getStandaloneQuery() { return standaloneQuery; }
    public void setStandaloneQuery(String v) { this.standaloneQuery = v; }
}
