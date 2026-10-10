package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.model.Plan;
import io.github.qifan777.server.agent.prompt.PromptManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ReportGeneratorNode implements NodeAction {
    private static final long REPORT_IDLE_COMPLETION_SECONDS = 20L;
    private static final String cleanJsonExample = "{\n"
        + "    \"title\": { \"text\": \"月度销售额\" },\n"
        + "    \"tooltip\": { \"trigger\": \"axis\" },\n"
        + "    \"xAxis\": { \"type\": \"category\", \"data\": [\"1月\", \"2月\"] },\n"
        + "    \"yAxis\": { \"type\": \"value\" },\n"
        + "    \"series\": [\n"
        + "        { \"type\": \"bar\", \"data\": [120, 200] }\n"
        + "    ]\n"
        + "}";

    private final ChatModel chatModel;
    private final PromptManager promptManager;
    public ReportGeneratorNode(ChatModel chatModel, PromptManager promptManager) {
        this.chatModel = chatModel; this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        // 可行性评估判定为“需要澄清”：不直接生成空报告，而是把澄清问题抛给用户追问
        String feasibilityResult = state.value(DataAgentSpec.Graph.StateKey.Execution.FEASIBILITY_RESULT, "");
        if (feasibilityResult.contains("需要澄清") || feasibilityResult.contains("需求澄清")) {
            return buildClarifyResponse(feasibilityResult);
        }
        String rewriteQuery = state.value(DataAgentSpec.Graph.StateKey.Recall.REWRITE_QUERY, "");
        Plan plan = Plan.getPlan(state);
        Map<String, String> result = state.value(DataAgentSpec.Graph.StateKey.Planning.EXECUTION_OUTPUT,
            new HashMap<String, String>());
        String summaryAndRecommendations = Plan.getCurrentStep(state).getToolParameters().getSummaryAndRecommendations();
        String answerMode = state.value(DataAgentSpec.Graph.StateKey.Input.ANSWER_MODE, "concise");
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Execution.REPORT_RESULT,
            generateReport(rewriteQuery, plan, result, summaryAndRecommendations, answerMode));
        return out;
    }

    private Flux<ChatResponse> generateReport(String userInput, Plan plan,
                                              Map<String, String> executionResults, String summaryAndRecommendations, String answerMode) {
        String userRequirementsAndPlan = buildUserRequirementsAndPlan(userInput, plan);
        String analysisStepsAndData = buildAnalysisStepsAndData(plan, executionResults);
        Map<String, Object> args = new HashMap<>();
        args.put("user_requirements_and_plan", userRequirementsAndPlan);
        args.put("analysis_steps_and_data", analysisStepsAndData);
        args.put("summary_and_recommendations", summaryAndRecommendations);
        args.put("json_example", cleanJsonExample);
        args.put("optimization_section", "");
        args.put("answer_mode", answerMode);
        String reportPrompt = promptManager.getReportGeneratorPlainPromptTemplate().render(args);
        Flux<ChatResponse> stream = ChatClient.create(chatModel)
            .prompt()
            .options(OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false)).build())
            .user(reportPrompt)
            .stream()
            .chatResponse();
        return withIdleCompletion(stream, REPORT_IDLE_COMPLETION_SECONDS);
    }

    /** ????? SSE ?????(????????????),?????????????,???????????????? */
    private static Flux<ChatResponse> withIdleCompletion(Flux<ChatResponse> source, long idleSeconds) {
        return source
            .timeout(Duration.ofSeconds(idleSeconds))
            .onErrorResume(TimeoutException.class, e -> Flux.empty());
    }

    private String buildUserRequirementsAndPlan(String userInput, Plan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户原始需求\n").append(userInput).append("\n\n");
        sb.append("## 执行计划概述\n");
        sb.append("**思考过程**: ").append(plan.getThoughtProcess()).append("\n\n");
        sb.append("## 详细执行步骤\n");
        List<Plan.PlanStep> steps = plan.getExecutionPlan();
        for (int index = 0; index < steps.size(); index++) {
            Plan.PlanStep step = steps.get(index);
            sb.append("### 步骤 ").append(index + 1).append(": 步骤编号 ").append(step.getStep()).append("\n");
            sb.append("**工具**: ").append(step.getToolToUse()).append("\n");
            sb.append("**参数描述**: ").append(step.getToolParameters().getInstruction()).append("\n\n");
        }
        return sb.toString();
    }

    private String buildAnalysisStepsAndData(Plan plan, Map<String, String> executionResults) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 数据执行结果\n");
        if (executionResults.isEmpty()) {
            sb.append("暂无执行结果数据\n");
        } else {
            List<Plan.PlanStep> executionPlan = plan.getExecutionPlan() == null ? List.of() : plan.getExecutionPlan();
            for (Map.Entry<String, String> entry : executionResults.entrySet()) {
                String stepKey = entry.getKey();
                String stepResult = entry.getValue();
                if (stepKey.endsWith("_analysis")) continue;
                sb.append("### ").append(stepKey).append("\n");
                try {
                    int stepIndex = Integer.parseInt(stepKey.replace("step_", "")) - 1;
                    if (stepIndex >= 0 && stepIndex < executionPlan.size()) {
                        Plan.PlanStep step = executionPlan.get(stepIndex);
                        sb.append("**步骤编号**: ").append(step.getStep()).append("\n");
                        sb.append("**使用工具**: ").append(step.getToolToUse()).append("\n");
                        Plan.PlanStep.ToolParameters params = step.getToolParameters();
                        if (params != null) {
                            sb.append("**参数描述**: ").append(params.getInstruction()).append("\n");
                            if (params.getSqlQuery() != null) {
                                sb.append("**执行SQL**: \n```sql\n").append(params.getSqlQuery()).append("\n```\n");
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    // ignore
                }
                sb.append("**执行结果**: \n```json\n").append(stepResult).append("\n```\n\n");
                String analysisKey = stepKey + "_analysis";
                String analysisResult = executionResults.get(analysisKey);
                if (analysisResult != null && !analysisResult.isBlank()) {
                    sb.append("**Python 分析结果**: ").append(analysisResult).append(" ");
                }
            }
        }
        return sb.toString();
    }

    /** 当需要澄清时：抽取澄清问题，以可读文本流式返回给用户（无需走 LLM 生成报告）。 */
    private Map<String, Object> buildClarifyResponse(String feasibilityResult) {
        String question = feasibilityResult;
        int idx = feasibilityResult.indexOf("【需求内容】：");
        if (idx >= 0) {
            question = feasibilityResult.substring(idx + "【需求内容】：".length()).trim();
        }
        String text = "为了给你更准确的分析，需要先确认一个口径问题：\n\n" + question
            + "\n\n（请直接补充说明，例如+指定统计口径或时间范围+，我会继续分析。）";
        List<Generation> gens = List.of(new Generation(new AssistantMessage(text)));
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Execution.REPORT_RESULT, Flux.just(new ChatResponse(gens)));
        return out;
    }
}
