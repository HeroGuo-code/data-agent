package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.model.IntentClassificationResult;
import io.github.qifan777.server.agent.prompt.PromptManager;
import java.util.HashMap;
import java.util.Map;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/** 前置意图识别节点：识别查询是否属于数据分析请求。问候/闲聊/无关不进入 NL2SQL 链路，交给提示词输出节点处理。 */
@Component
public class IntentRecognitionNode implements NodeAction {

    private final ChatModel chatModel;
    private final PromptManager promptManager;

    public IntentRecognitionNode(ChatModel chatModel, PromptManager promptManager) {
        this.chatModel = chatModel;
        this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String userInput = state.value(DataAgentSpec.Graph.StateKey.Input.USER_INPUT, "");
        String multiTurn = state.value(DataAgentSpec.Graph.StateKey.Input.MULTI_TURN_CONTEXT, "（无）");
        BeanOutputConverter<IntentClassificationResult> converter =
            new BeanOutputConverter<>(IntentClassificationResult.class);
        Map<String, Object> args = new HashMap<>();
        args.put("latest_query", userInput);
        args.put("multi_turn", multiTurn);
        args.put("format", converter.getFormat());
        String prompt = promptManager.getIntentRecognitionPromptTemplate().render(args);
        String response = ChatClient.create(chatModel)
            .prompt()
            .options(OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false)).build())
            .user(prompt)
            .call()
            .content();
        String classification = "";
        if (response != null) {
            try {
                IntentClassificationResult result =
                    new BeanOutputConverter<IntentClassificationResult>(IntentClassificationResult.class).convert(response);
                if (result != null && result.getClassification() != null) {
                    classification = result.getClassification();
                }
            } catch (Exception e) {
                classification = response;
            }
        }
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Intent.CLASSIFICATION, classification);
        out.put(DataAgentSpec.Graph.StateKey.Intent.TYPE, detectType(userInput, classification));
        return out;
    }

    /** 问候 -> greeting，闲聊/无关 -> chat，其余 -> data。 */
    static String detectType(String userInput, String classification) {
        String u = userInput == null ? "" : userInput.trim();
        String c = classification == null ? "" : classification;
        if (isGreeting(u) || c.contains("问候") || c.contains("打招呼")) {
            return DataAgentSpec.Graph.StateKey.Intent.GREETING;
        }
        if (c.contains("闲聊") || c.contains("无关") || c.contains("聊天")) {
            return DataAgentSpec.Graph.StateKey.Intent.CHAT;
        }
        return DataAgentSpec.Graph.StateKey.Intent.DATA;
    }

    private static boolean isGreeting(String u) {
        if (u.isEmpty()) return false;
        String lower = u.toLowerCase();
        String[] words = {"你好", "您好", "hello", "hi ", "嗨", "哈喽", "在吗", "在么", "hey", "你好啊", "你好呀", "早上好", "晚上好", "下午好", "中午好"};
        for (String w : words) {
            if (u.contains(w) || lower.contains(w)) return true;
        }
        return false;
    }
}
