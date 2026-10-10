package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.model.Plan;
import io.github.qifan777.server.agent.model.Schema;
import io.github.qifan777.server.agent.prompt.PromptManager;
import io.github.qifan777.server.shared.json.JsonUtil;
import java.util.HashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
public class PlannerNode implements NodeAction {
    private static final int MAX_REPLAN = 1;
    private static final String[] AGG_KEYWORDS = {
        "历史", "汇总", "统计", "合计", "总计", "平均", "排行",
        "GMV", "销售额", "销量", "消费", "明细", "客单"
    };

    private final ChatModel chatModel;
    private final PromptManager promptManager;

    public PlannerNode(ChatModel chatModel, PromptManager promptManager) {
        this.chatModel = chatModel;
        this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String rewriteQuery = state.value(DataAgentSpec.Graph.StateKey.Recall.REWRITE_QUERY, "");
        Schema schemeDto = Schema.fromState(state);
        String feedbackContent = state.value(DataAgentSpec.Graph.StateKey.HumanReview.CONFIRMATION_FEEDBACK, "");
        String schemePrompt = schemeDto.buildSchemePrompt();
        String evidence = state.value(DataAgentSpec.Graph.StateKey.Recall.EVIDENCE, "");
        BeanOutputConverter<Plan> converter = new BeanOutputConverter<>(Plan.class);
        Map<String, Object> args = new HashMap<>();
        args.put("user_question", rewriteQuery);
        args.put("schema", schemePrompt);
        args.put("evidence", evidence);
        args.put("semantic_model", "");
        args.put("format", converter.getFormat());

        // 兜底重规划：聚合/汇总/历史类问题若被压成 <=1 条原始 SQL，
        // 携带明确反馈重规划一次，避免“SQL 执行完直接出报告”的残缺答案。
        String validationFeedback = feedbackContent;
        int attempt = 0;
        String plan;
        while (true) {
            args.put("plan_validation_error", validationFeedback);
            String prompt = promptManager.getPlannerPromptTemplate().render(args);
            plan = ChatClient.create(chatModel)
                .prompt()
                .options(OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false)).build())
                .user(prompt)
                .call()
                .content();
            Plan parsed = parse(plan);
            if (isDegeneratePlan(rewriteQuery, parsed) && attempt < MAX_REPLAN) {
                attempt++;
                validationFeedback = "上次计划过简（<=1 条 SQL），无法覆盖聚合/汇总/历史类问题。请拆分为多个步骤：先分步查询订单明细、退款/售后等原始数据，再做聚合汇总（SUM/COUNT/GROUP BY），确有必要可追加 PYTHON_GENERATE_NODE；禁止只生成 1 条原始 SELECT。";
                continue;
            }
            break;
        }

        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Planning.PLAN, plan);
        return out;
    }

    private static Plan parse(String plan) {
        if (plan == null || plan.isBlank()) return null;
        try {
            return JsonUtil.fromJson(plan, Plan.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** 聚合/汇总/历史类问题若只剩 1 个可执行步骤，判定为退化计划。 */
    private static boolean isDegeneratePlan(String question, Plan plan) {
        if (plan == null || plan.getExecutionPlan() == null || plan.getExecutionPlan().isEmpty()) return false;
        int exec = 0;
        for (Plan.PlanStep s : plan.getExecutionPlan()) {
            String t = s.getToolToUse() == null ? "" : s.getToolToUse();
            if (!t.isEmpty()
                && !t.equals(DataAgentSpec.Graph.Node.REPORT_GENERATION)
                && !t.equals("END") && !t.equals("NONE")) {
                exec++;
            }
        }
        if (exec >= 2) return false;
        return mentionsAggregation(question);
    }

    private static boolean mentionsAggregation(String question) {
        if (question == null) return false;
        for (String k : AGG_KEYWORDS) {
            if (question.contains(k)) return true;
        }
        return false;
    }
}
