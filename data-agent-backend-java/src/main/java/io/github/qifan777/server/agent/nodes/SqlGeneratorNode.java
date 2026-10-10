package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.model.Plan;
import io.github.qifan777.server.agent.model.Schema;
import io.github.qifan777.server.agent.prompt.PromptManager;
import io.github.qifan777.server.shared.markdown.MarkdownParserUtil;
import java.util.HashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
public class SqlGeneratorNode implements NodeAction {
    private final ChatModel chatModel;
    private final PromptManager promptManager;

    public SqlGeneratorNode(ChatModel chatModel, PromptManager promptManager) {
        this.chatModel = chatModel;
        this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Plan.PlanStep step = Plan.getCurrentStep(state);
        String instruction = step.getToolParameters().getInstruction();
        if (instruction == null) throw new RuntimeException("sql生成步骤 instruction为空");
        Schema schemeDto = Schema.fromState(state);
        String rewriteQuery = state.value(DataAgentSpec.Graph.StateKey.Recall.REWRITE_QUERY, "");
        String evidence = state.value(DataAgentSpec.Graph.StateKey.Recall.EVIDENCE, "");
        String dialect = "sqlite";
        Map<String, Object> args = new HashMap<>();
        args.put("dialect", dialect);
        args.put("question", rewriteQuery);
        args.put("schema_info", schemeDto.buildSchemePrompt());
        args.put("evidence", evidence);
        args.put("execution_description", instruction);
        String sqlPrompt = promptManager.getNewSqlGeneratorPromptTemplate().render(args);
        String sql = ChatClient.create(chatModel)
            .prompt()
            .system(sqlPrompt)
            .options(OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false)).build())
            .call()
            .content();
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Execution.SQL_GENERATION_RESULT, extractSql(sql));
        return out;
    }
    private static final java.util.regex.Pattern SQL_START =
        java.util.regex.Pattern.compile("(?is)\\b(select|with)\\b");

    private static String extractSql(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.contains("```")) {
            String fenced = MarkdownParserUtil.extractRawText(s).trim();
            if (!fenced.isEmpty()) s = fenced;
        }
        java.util.regex.Matcher m = SQL_START.matcher(s);
        if (m.find()) {
            s = s.substring(m.start());
        }
        int lastSemi = s.lastIndexOf(';');
        if (lastSemi >= 0) {
            s = s.substring(0, lastSemi + 1);
        }
        return s.trim();
    }
}