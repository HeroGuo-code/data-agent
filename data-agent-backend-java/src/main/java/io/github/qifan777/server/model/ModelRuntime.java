package io.github.qifan777.server.model;

import io.github.qifan777.server.model.ModelProfile;
import java.time.Duration;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

/**
 * Holds the live OpenAI chat/embedding clients built from the persisted model profiles.
 * Consumers reach them through the DynamicChatModel / DynamicEmbeddingModel delegating beans.
 * Call reload() after saving a profile to apply it immediately (no restart).
 *
 * 注意：OpenAiChatModel 内置的 RetryTemplate 默认 10 次指数退避重试（累计最长可达十几分钟），
 * 一旦网关偶发 5xx/超时就会让一个节点默默卡十几分钟（前端表现为“证据召回一直转圈”）。
 * 这里显式改为 3 次固定间隔重试 + 明确的连接/读取超时，做到失败快速暴露。
 */
public class ModelRuntime {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 180000;
    private static final int RETRY_MAX_ATTEMPTS = 3;
    private static final int RETRY_FIXED_BACKOFF_MS = 1000;

    private final ModelConfigService config;
    private volatile ChatModel chat = null;
    private volatile EmbeddingModel embedd = null;

    public ModelRuntime(ModelConfigService config) {
        this.config = config;
    }

    public synchronized void reload() {
        ModelProfile llm = config.load("llm");
        ModelProfile emb = config.load("embedding");
        if (llm != null && llm.isEnabled() && hasText(llm.getBaseUrl()) && hasText(llm.getModelName())) {
            OpenAiApi api = buildApi(llm.getBaseUrl(), orDefault(llm.getApiKey(), "sk-noop"));
            OpenAiChatOptions opts = OpenAiChatOptions.builder().model(llm.getModelName()).build();
            this.chat = OpenAiChatModel.builder().openAiApi(api).defaultOptions(opts).retryTemplate(fastRetry()).build();
        } else {
            this.chat = null;
        }
        if (emb != null && emb.isEnabled() && hasText(emb.getBaseUrl()) && hasText(emb.getModelName())) {
            OpenAiApi api = buildApi(emb.getBaseUrl(), orDefault(emb.getApiKey(), "unused"));
            OpenAiEmbeddingOptions opts = OpenAiEmbeddingOptions.builder().model(emb.getModelName()).build();
            this.embedd = new OpenAiEmbeddingModel(api, MetadataMode.EMBED, opts, fastRetry());
        } else {
            this.embedd = null;
        }
    }

    private static OpenAiApi buildApi(String baseUrl, String apiKey) {
        SimpleClientHttpRequestFactory syncFactory = new SimpleClientHttpRequestFactory();
        syncFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        syncFactory.setReadTimeout(READ_TIMEOUT_MS);
        RestClient.Builder restBuilder = RestClient.builder().requestFactory(syncFactory);
        WebClient.Builder webBuilder = WebClient.builder().clientConnector(new ReactorClientHttpConnector(
            HttpClient.create().responseTimeout(Duration.ofMillis(READ_TIMEOUT_MS))));
        return OpenAiApi.builder().baseUrl(baseUrl).apiKey(apiKey)
            .restClientBuilder(restBuilder).webClientBuilder(webBuilder).build();
    }

    private static RetryTemplate fastRetry() {
        return RetryTemplate.builder().maxAttempts(RETRY_MAX_ATTEMPTS).fixedBackoff(RETRY_FIXED_BACKOFF_MS).build();
    }

    public ChatModel chat() {
        ChatModel c = this.chat;
        if (c == null) { reload(); c = this.chat; }
        return c;
    }
    public EmbeddingModel embed() {
        EmbeddingModel e = this.embedd;
        if (e == null) { reload(); e = this.embedd; }
        return e;
    }
    public int embeddingDimensions() {
        ModelProfile emb = config.load("embedding");
        Integer d = emb == null ? null : emb.getDimensions();
        return d != null ? d : 1024;
    }

    private static boolean hasText(String s) { return s != null && !s.trim().isEmpty(); }
    private static String orDefault(String s, String d) { return (s == null || s.trim().isEmpty()) ? d : s.trim(); }
}
