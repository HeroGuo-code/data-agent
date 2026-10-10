package io.github.qifan777.server.model;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

/** Delegates ChatModel calls to the current runtime chat client so config changes apply live. */
public class DynamicChatModel implements ChatModel {
    private final ModelRuntime runtime;
    public DynamicChatModel(ModelRuntime runtime) { this.runtime = runtime; }
    @Override public ChatResponse call(Prompt prompt) { return runtime.chat().call(prompt); }
    @Override public Flux<ChatResponse> stream(Prompt prompt) { return runtime.chat().stream(prompt); }
}
