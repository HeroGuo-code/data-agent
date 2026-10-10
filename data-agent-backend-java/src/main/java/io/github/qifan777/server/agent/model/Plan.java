package io.github.qifan777.server.agent.model;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.shared.json.JsonUtil;
import java.util.List;

public class Plan {
    @JsonProperty("thought_process")
    @JsonPropertyDescription("简要描述你的分析思路。必须明确提到你检索了哪些表和字段")
    private String thoughtProcess;

    @JsonProperty("execution_plan")
    @JsonPropertyDescription("执行计划的步骤列表")
    private List<PlanStep> executionPlan;

    public static Plan getPlan(OverAllState state) {
        String planStr = state.value(DataAgentSpec.Graph.StateKey.Planning.PLAN, "");
        Plan plan = JsonUtil.fromJson(planStr, Plan.class);
        if (plan == null) throw new RuntimeException("plan json反序列化失败");
        return plan;
    }

    public static PlanStep getCurrentStep(OverAllState state) {
        Plan plan = getPlan(state);
        int step = state.value(DataAgentSpec.Graph.StateKey.Planning.CURRENT_STEP, 1);
        return plan.executionPlan.get(step - 1);
    }

    public String getThoughtProcess() { return thoughtProcess; }
    public void setThoughtProcess(String v) { this.thoughtProcess = v; }
    public List<PlanStep> getExecutionPlan() { return executionPlan; }
    public void setExecutionPlan(List<PlanStep> v) { this.executionPlan = v; }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlanStep {
        @JsonProperty("step")
        @JsonPropertyDescription("步骤顺序号")
        private int step;

        @JsonProperty("tool_to_use")
        @JsonPropertyDescription("工具名称")
        private String toolToUse;

        @JsonProperty("tool_parameters")
        @JsonPropertyDescription("工具参数")
        private ToolParameters toolParameters;

        public int getStep() { return step; }
        public void setStep(int v) { this.step = v; }
        public String getToolToUse() { return toolToUse; }
        public void setToolToUse(String v) { this.toolToUse = v; }
        public ToolParameters getToolParameters() { return toolParameters; }
        public void setToolParameters(ToolParameters v) { this.toolParameters = v; }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class ToolParameters {
            @JsonProperty("instruction")
            private String instruction;
            @JsonProperty("summary_and_recommendations")
            private String summaryAndRecommendations;
            @JsonProperty("sql_query")
            private String sqlQuery;

            public String getInstruction() { return instruction; }
            public void setInstruction(String v) { this.instruction = v; }
            public String getSummaryAndRecommendations() { return summaryAndRecommendations; }
            public void setSummaryAndRecommendations(String v) { this.summaryAndRecommendations = v; }
            public String getSqlQuery() { return sqlQuery; }
            public void setSqlQuery(String v) { this.sqlQuery = v; }
        }
    }
}
