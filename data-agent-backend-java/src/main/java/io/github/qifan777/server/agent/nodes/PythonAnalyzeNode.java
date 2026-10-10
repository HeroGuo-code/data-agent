package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.prompt.PromptManager;
import io.github.qifan777.server.shared.python.PythonExecutionResult;
import java.util.HashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
public class PythonAnalyzeNode implements NodeAction {
    private final ChatModel chatModel;
    private final PromptManager promptManager;
    public PythonAnalyzeNode(ChatModel chatModel, PromptManager promptManager) {
        this.chatModel = chatModel; this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String rewriteQuery = state.value(DataAgentSpec.Graph.StateKey.Recall.REWRITE_QUERY, "");
        PythonExecutionResult pythonOutput = state.value(DataAgentSpec.Graph.StateKey.Execution.PYTHON_EXECUTION_RESULT, PythonExecutionResult.class).orElseThrow();
        Integer step = state.value(DataAgentSpec.Graph.StateKey.Planning.CURRENT_STEP, 1);
        Map<String, String> map = state.value(DataAgentSpec.Graph.StateKey.Planning.EXECUTION_OUTPUT, new HashMap<String,String>());
        Map<String, Object> args = new HashMap<>();
        args.put("python_output", pythonOutput.toString());
        args.put("user_query", rewriteQuery);
        String prompt = promptManager.getPythonAnalyzePromptTemplate().render(args);
        String analyze = ChatClient.create(chatModel)
            .prompt()
            .options(OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false)).build())
            .user(prompt)
            .call()
            .content();
        map.put("step_" + step + "_analysis", analyze == null ? "" : analyze);
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Planning.EXECUTION_OUTPUT, map);
        out.put(DataAgentSpec.Graph.StateKey.Planning.CURRENT_STEP, step + 1);
        return out;
    }
}
