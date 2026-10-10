package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.model.Plan;
import io.github.qifan777.server.agent.model.Schema;
import io.github.qifan777.server.agent.prompt.PromptManager;
import io.github.qifan777.server.graph.nodes.SqlExecuteNode;
import io.github.qifan777.server.shared.json.JsonUtil;
import io.github.qifan777.server.shared.markdown.MarkdownParserUtil;
import java.util.HashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
public class PythonGeneratorNode implements NodeAction {
    private final ChatModel chatModel;
    private final PromptManager promptManager;
    public PythonGeneratorNode(ChatModel chatModel, PromptManager promptManager) {
        this.chatModel = chatModel; this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Schema schemeDto = Schema.fromState(state);
        SqlExecuteNode.Result result = state.value(DataAgentSpec.Graph.StateKey.Execution.SQL_EXECUTION_RESULT, SqlExecuteNode.Result.class).orElseThrow();
        Plan.PlanStep executionStep = Plan.getCurrentStep(state);
        Map<String, Object> args = new HashMap<>();
        args.put("python_memory", "500");
        args.put("python_timeout", "500");
        args.put("database_schema", schemeDto.buildSchemePrompt());
        args.put("sample_input", JsonUtil.toJson(result.getResultSet().getData()));
        args.put("plan_description", JsonUtil.toJson(executionStep.getToolParameters()));
        String prompt = promptManager.getPythonGeneratorPromptTemplate().render(args);
        String pythonCode = ChatClient.create(chatModel)
            .prompt()
            .system(prompt)
            .options(OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false)).build())
            .call()
            .content();
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Execution.PYTHON_GENERATION_RESULT, MarkdownParserUtil.extractRawText(pythonCode));
        return out;
    }
}
