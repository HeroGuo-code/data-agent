package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agentconfig.AgentConfigService;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/** 提示词输出节点：意图识别为问候/闲聊/无关时，输出配置好的开场白（问候）或按配置的人设提示词直接交给大模型回复（闲聊/无关），并流式返回。 */
@Component
public class PromptOutputNode implements NodeAction {
    private final ChatModel chatModel;
    private final AgentConfigService configService;

    public PromptOutputNode(ChatModel chatModel, AgentConfigService configService) {
        this.chatModel = chatModel;
        this.configService = configService;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String type = state.value(DataAgentSpec.Graph.StateKey.Intent.TYPE, DataAgentSpec.Graph.StateKey.Intent.CHAT);
        String userInput = state.value(DataAgentSpec.Graph.StateKey.Input.USER_INPUT, "");
        String multiTurn = state.value(DataAgentSpec.Graph.StateKey.Input.MULTI_TURN_CONTEXT, "（无）");
        var cfg = configService.getConfig();
        Map<String, Object> out = new HashMap<>();
        if (DataAgentSpec.Graph.StateKey.Intent.GREETING.equals(type)) {
            String opening = (cfg.getOpeningMessage() == null || cfg.getOpeningMessage().isBlank())
                ? defaultOpening() : cfg.getOpeningMessage();
            out.put(DataAgentSpec.Graph.StateKey.Execution.REPORT_RESULT, plainFlux(opening));
        } else {
            String persona = (cfg.getPersonaPrompt() == null || cfg.getPersonaPrompt().isBlank())
                ? defaultPersona() : cfg.getPersonaPrompt();
            out.put(DataAgentSpec.Graph.StateKey.Execution.REPORT_RESULT, chatFlux(persona, userInput, multiTurn));
        }
        return out;
    }

    private Flux<ChatResponse> chatFlux(String persona, String userInput, String multiTurn) {
        String prompt = (multiTurn == null || multiTurn.isBlank() || "（无）".equals(multiTurn.trim()))
            ? userInput : "历史对话上下文：\n" + multiTurn + "\n\n用户最新消息：\n" + userInput;
        Flux<ChatResponse> stream = ChatClient.create(chatModel)
            .prompt()
            .options(OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false)).build())
            .system(persona)
            .user(prompt)
            .stream()
            .chatResponse();
        return withIdleCompletion(stream, 30L);
    }

    private static Flux<ChatResponse> plainFlux(String text) {
        List<Generation> gens = List.of(new Generation(new AssistantMessage(text)));
        return Flux.just(new ChatResponse(gens));
    }

    private static Flux<ChatResponse> withIdleCompletion(Flux<ChatResponse> source, long idleSeconds) {
        return source
            .timeout(Duration.ofSeconds(idleSeconds))
            .onErrorResume(TimeoutException.class, e -> Flux.empty());
    }

    private static String defaultOpening() {
        return "你好呀😊 我是标准化智能助手小鑫，如果你有电商经营数据相关的问题，都可以随时告诉我哦！";
    }

    private static String defaultPersona() {
        return "你是标准化智能助手「小鑫」，负责帮助用户查询和分析电商经营数据。请用自然、友好、简洁的语气回复用户，不要编造数据。";
    }
}
