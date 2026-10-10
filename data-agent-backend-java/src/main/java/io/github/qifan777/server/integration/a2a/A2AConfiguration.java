package io.github.qifan777.server.integration.a2a;

import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.events.InMemoryQueueManager;
import io.a2a.server.events.QueueManager;
import io.a2a.server.requesthandlers.DefaultRequestHandler;
import io.a2a.server.tasks.BasePushNotificationSender;
import io.a2a.server.tasks.InMemoryPushNotificationConfigStore;
import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.server.tasks.PushNotificationConfigStore;
import io.a2a.server.tasks.PushNotificationSender;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentInterface;
import io.a2a.spec.AgentSkill;
import io.a2a.transport.jsonrpc.handler.JSONRPCHandler;
import java.util.List;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class A2AConfiguration {

    @Bean
    public AgentCard agentCard(@Value("${a2a.public.url:http://localhost:3500/api/a2a/jsonrpc}") String publicUrl) {
        return new AgentCard.Builder()
            .name("sqlAgent")
            .description("专业的SQL生成Agent")
            .defaultInputModes(List.of("text/plain"))
            .defaultOutputModes(List.of("text/plain"))
            .capabilities(new AgentCapabilities.Builder()
                .streaming(true)
                .pushNotifications(true)
                .stateTransitionHistory(true)
                .build())
            .skills(List.of(new AgentSkill.Builder()
                .id("sql")
                .name("sql generator")
                .description("Generate a SQL query")
                .tags(List.of("sql", "query"))
                .build()))
            // 浏览器端 A2A 客户端会直接向该地址发请求：
            // 开发模式默认走 Vite(3500) 代理；Docker 部署用环境变量 A2A_PUBLIC_URL 指向 nginx(8080) 同源代理
            .url(publicUrl)
            .additionalInterfaces(List.of(new AgentInterface("JSONRPC", publicUrl)))
            .version("1.0")
            .build();
    }

    @Bean
    public InMemoryTaskStore taskStore() {
        return new InMemoryTaskStore();
    }

    @Bean
    public InMemoryPushNotificationConfigStore pushNotificationConfigStore() {
        return new InMemoryPushNotificationConfigStore();
    }

    @Bean
    public BasePushNotificationSender pushNotificationSender(PushNotificationConfigStore pushNotificationConfigStore) {
        return new BasePushNotificationSender(pushNotificationConfigStore);
    }

    @Bean
    public QueueManager queueManager(InMemoryTaskStore taskStore) {
        return new InMemoryQueueManager(taskStore);
    }

    @Bean
    public Integer agentCompletionTimeoutSeconds(@Value("${a2a.blocking.agent.timeout.seconds:30}") Integer timeout) {
        return timeout;
    }

    @Bean
    public JSONRPCHandler jsonRpcHandler(AgentCard agentCard, AgentExecutor agentExecutor,
                                         TaskStore taskStore, QueueManager queueManager,
                                         PushNotificationConfigStore pushNotificationConfigStore,
                                         PushNotificationSender pushNotificationSender) {
        var pool = Executors.newFixedThreadPool(5);
        var handler = DefaultRequestHandler.create(agentExecutor, taskStore, queueManager,
            pushNotificationConfigStore, pushNotificationSender, pool);
        return new JSONRPCHandler(agentCard, handler, pool);
    }
}
