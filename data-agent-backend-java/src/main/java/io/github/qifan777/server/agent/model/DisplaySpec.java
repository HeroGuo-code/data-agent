package io.github.qifan777.server.agent.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

public class DisplaySpec {
    @JsonPropertyDescription("图表类型，取值范围：table、bar、line、pie等")
    private String type;
    @JsonPropertyDescription("图表标题")
    private String title;
    @JsonPropertyDescription("X轴字段名")
    private String x;
    @JsonPropertyDescription("Y轴字段名列")
    private List<String> y;

    public DisplaySpec() {}
    public DisplaySpec(String type, String title, String x, List<String> y) {
        this.type = type; this.title = title; this.x = x; this.y = y;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getX() { return x; }
    public void setX(String x) { this.x = x; }
    public List<String> getY() { return y; }
    public void setY(List<String> y) { this.y = y; }
}
