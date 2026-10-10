package io.github.qifan777.server.model;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Wires the dynamic, config-driven models as the primary ChatModel / EmbeddingModel beans. */
@Configuration
public class ModelConfigBeans {

    @Bean
    public ModelRuntime modelRuntime(ModelConfigService config) {
        return new ModelRuntime(config);
    }

    @Bean
    public DynamicChatModel dynamicChatModel(ModelRuntime runtime) {
        return new DynamicChatModel(runtime);
    }

    @Bean
    public DynamicEmbeddingModel dynamicEmbeddingModel(ModelRuntime runtime) {
        return new DynamicEmbeddingModel(runtime);
    }

    @Bean
    @Primary
    public ChatModel chatModel(DynamicChatModel dynamic) {
        return dynamic;
    }

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(DynamicEmbeddingModel dynamic) {
        return dynamic;
    }
}
