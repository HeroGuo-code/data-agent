package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.model.Schema;
import io.github.qifan777.server.agent.prompt.PromptManager;
import java.util.HashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
public class FeasibilityAssessmentNode implements NodeAction {
    private final ChatModel chatModel;
    private final PromptManager promptManager;

    public FeasibilityAssessmentNode(ChatModel chatModel, PromptManager promptManager) {
        this.chatModel = chatModel;
        this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String rewriteQuery = state.value(DataAgentSpec.Graph.StateKey.Recall.REWRITE_QUERY, "");
        Schema schema = Schema.fromState(state);
        String evidence = state.value(DataAgentSpec.Graph.StateKey.Recall.EVIDENCE, "");
        String multiTurn = state.value(DataAgentSpec.Graph.StateKey.Input.MULTI_TURN_CONTEXT, "（无）");
        Map<String, Object> args = new HashMap<>();
        args.put("recalled_schema", schema.buildSchemePrompt());
        args.put("evidence", evidence);
        args.put("canonical_query", rewriteQuery);
        args.put("multi_turn", multiTurn);
        String prompt = promptManager.getFeasibilityAssessmentPromptTemplate().render(args);
        String feasibilityAssessment = ChatClient.create(chatModel)
            .prompt()
            .options(OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false)).build())
            .user(prompt)
            .call()
            .content();
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Execution.FEASIBILITY_RESULT, feasibilityAssessment);
        return out;
    }
}
