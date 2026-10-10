package io.github.qifan777.server.integration.a2a;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import io.a2a.server.agentexecution.AgentExecutor;
import io.a2a.server.agentexecution.RequestContext;
import io.a2a.server.events.EventQueue;
import io.a2a.server.tasks.TaskStore;
import io.a2a.server.tasks.TaskUpdater;
import io.a2a.spec.DataPart;
import io.a2a.spec.Message;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TextPart;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.trace.TaskTraceService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class GraphAgentExecutor implements AgentExecutor {
    private final StateGraph stateGraph;
    private final TaskStore taskStore;
    private final MemorySaver saver = new MemorySaver();
    private final TaskTraceService traceService;

    public GraphAgentExecutor(StateGraph stateGraph, TaskStore taskStore, TaskTraceService traceService) {
        this.stateGraph = stateGraph;
        this.taskStore = taskStore;
        this.traceService = traceService;
    }

    @Override
    public void execute(RequestContext context, EventQueue eventQueue) {
        TaskUpdater taskUpdater = new TaskUpdater(context, eventQueue);
        AtomicInteger artifactNum = new AtomicInteger();
        Message metaMessage = context.getMessage();
        Object hitlValue = metaMessage.getMetadata() == null ? null
            : metaMessage.getMetadata().get(DataAgentSpec.MessageMetadataKey.HUMAN_IN_THE_LOOP);
        boolean humanInTheLoop = !Boolean.FALSE.equals(hitlValue);
        CompiledGraph compiledGraph;
        try {
            CompileConfig config;
            if (humanInTheLoop) {
                config = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder().register(saver).build())
                    .interruptBefore(DataAgentSpec.Graph.Node.INTERRUPT_NODE)
                    .build();
            } else {
                config = CompileConfig.builder()
                    .saverConfig(SaverConfig.builder().register(saver).build())
                    .build();
            }
            compiledGraph = stateGraph.compile(config);
        } catch (GraphStateException e) {
            throw new RuntimeException(e);
        }

        Consumer<NodeOutput> handleNodeOutput = (NodeOutput nodeOutput) -> {
            if (nodeOutput instanceof StreamingOutput<?> so) {
                if (so.getOutputType() == OutputType.GRAPH_NODE_STREAMING) {
                    org.springframework.ai.chat.messages.Message message = so.message();
                    String msgText = message == null ? null : message.getText();
                    if (msgText != null && !msgText.isEmpty()) {
                        taskUpdater.addArtifact(
                            List.of(new TextPart(msgText)),
                            Integer.toString(artifactNum.incrementAndGet()),
                            nodeOutput.node(),
                            Map.of("outputType", so.getOutputType()));
                    }
                } else if (so.getOutputType() == OutputType.GRAPH_NODE_FINISHED) {
                    taskUpdater.addArtifact(
                        List.of(new DataPart(nodeOutput.state().data())),
                        Integer.toString(artifactNum.incrementAndGet()),
                        nodeOutput.node(),
                        Map.of("outputType", so.getOutputType()));
                }
                return;
            }
            taskUpdater.addArtifact(
                List.of(new DataPart(nodeOutput.state().data())),
                Integer.toString(artifactNum.incrementAndGet()),
                nodeOutput.node(),
                Map.of());
        };

        Message message = context.getMessage();
        Task existingTask = message.getTaskId() == null ? null : taskStore.get(message.getTaskId());
        if (existingTask != null) {
            RunnableConfig runnableConfig = RunnableConfig.builder().threadId(existingTask.getId()).build();
            Object approved = message.getMetadata() == null ? null : message.getMetadata().get(DataAgentSpec.MessageMetadataKey.CONFIRMATION_APPROVED);
            Object feedback = message.getMetadata() == null ? null : message.getMetadata().get(DataAgentSpec.MessageMetadataKey.CONFIRMATION_FEEDBACK);
            RunnableConfig resumedConfig;
            try {
                resumedConfig = compiledGraph.updateState(runnableConfig,
                    Map.of(
                        DataAgentSpec.Graph.StateKey.HumanReview.CONFIRMATION_APPROVED, approved,
                        DataAgentSpec.Graph.StateKey.HumanReview.CONFIRMATION_FEEDBACK, feedback));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            CompletableFlux(compiledGraph.stream(null, resumedConfig), taskUpdater, handleNodeOutput,
                () -> onComplete(compiledGraph, taskUpdater, runnableConfig));
            return;
        }

        Task newTask = newTask(message);
        eventQueue.enqueueEvent(newTask);
        String input = ((TextPart) message.getParts().stream()
            .filter(p -> p instanceof TextPart).findFirst().orElseThrow()).getText();
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(newTask.getId()).build();
        Map<String, Object> initial = new java.util.HashMap<>();
        initial.put(DataAgentSpec.Graph.StateKey.Input.USER_INPUT, input);
        initial.put(DataAgentSpec.Graph.StateKey.Input.TRACE_TASK_ID, newTask.getId());
        Object roleMeta = message.getMetadata() == null ? null : message.getMetadata().get("role");
        initial.put(DataAgentSpec.Graph.StateKey.Input.ROLE, roleMeta == null ? "运营" : String.valueOf(roleMeta));
        initial.put(DataAgentSpec.Graph.StateKey.Input.DATABASE_ID,
            message.getMetadata() == null ? null : message.getMetadata().get(DataAgentSpec.MessageMetadataKey.DATABASE_ID));
        Object multiTurnCtx = message.getMetadata() == null ? null
            : message.getMetadata().get(DataAgentSpec.MessageMetadataKey.MULTI_TURN_CONTEXT);
        initial.put(DataAgentSpec.Graph.StateKey.Input.MULTI_TURN_CONTEXT,
            multiTurnCtx == null ? "无" : String.valueOf(multiTurnCtx));
        initial.put(DataAgentSpec.Graph.StateKey.Input.HUMAN_IN_THE_LOOP, humanInTheLoop);
        Object answerModeMeta = message.getMetadata() == null ? null
            : message.getMetadata().get(DataAgentSpec.MessageMetadataKey.ANSWER_MODE);
        String answerMode = answerModeMeta == null ? "concise" : String.valueOf(answerModeMeta);
        initial.put(DataAgentSpec.Graph.StateKey.Input.ANSWER_MODE, answerMode);
        CompletableFlux(compiledGraph.stream(initial, runnableConfig), taskUpdater, handleNodeOutput,
            () -> onComplete(compiledGraph, taskUpdater, runnableConfig));
    }

        private static final String ARTIFACT_OUTPUT_FINISHED = "GRAPH_NODE_FINISHED";

    private long pipelineTimeoutSeconds() {
        String v = System.getenv("DATA_AGENT_PIPELINE_TIMEOUT_SECONDS");
        try {
            if (v != null && !v.isBlank()) return Long.parseLong(v.trim());
        } catch (NumberFormatException ignored) {
        }
        return 900;
    }

    private void CompletableFlux(Flux<NodeOutput> stream, TaskUpdater taskUpdater,
                                 Consumer<NodeOutput> onNext, Runnable onComplete) {
        long timeoutSec = pipelineTimeoutSeconds();
        try {
            stream
                .timeout(Duration.ofSeconds(timeoutSec))
                .doOnNext(onNext)
                .doOnComplete(onComplete)
                .blockLast(Duration.ofSeconds(timeoutSec + 60));
        } catch (Exception e) {
            boolean isTimeout = isTimeoutException(e);
            String reason = isTimeout
                ? "⏱ 本次查询处理时间超过 " + timeoutSec + " 秒，为避免长时间占用已自动中止。建议缩小时间范围或拆分问题后再试。"
                : "⚠ 查询执行遇到异常，请稍后重试。（" + safeMessage(e) + "）";
            taskUpdater.addArtifact(
                List.of(new TextPart(reason)),
                "watchdog-result",
                DataAgentSpec.Graph.Node.REPORT_GENERATION,
                Map.of("outputType", ARTIFACT_OUTPUT_FINISHED),
                true, true);
            // 异常/超时也收敛任务状态（trace 置为完成、发出 COMPLETED），避免前端与调试页卡在“运行中”
            onComplete.run();
        }
    }

    private boolean isTimeoutException(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 8) {
            if (cur instanceof java.util.concurrent.TimeoutException) return true;
            cur = cur.getCause();
            depth++;
        }
        return false;
    }
    private String safeMessage(Throwable t) {
        String m = t == null || t.getMessage() == null ? "" : t.getMessage();
        return m.length() > 200 ? m.substring(0, 200) : m;
    }

    @Override
    public void cancel(RequestContext context, EventQueue eventQueue) {
        // no-op
    }

    private Task newTask(Message request) {
        String contextId = request.getContextId();
        if (contextId == null || contextId.isEmpty()) contextId = UUID.randomUUID().toString();
        String id = UUID.randomUUID().toString();
        if (request.getTaskId() != null && !request.getTaskId().isEmpty()) id = request.getTaskId();
        return new Task(id, contextId, new TaskStatus(TaskState.SUBMITTED), null, List.of(request), null);
    }

    private void onComplete(CompiledGraph compiledGraph, TaskUpdater taskUpdater, RunnableConfig runnableConfig) {
        String threadId = runnableConfig.threadId().orElse("");
        String next = null;
        try {
            StateSnapshot snapshot = compiledGraph.getState(runnableConfig);
            next = snapshot == null ? null : snapshot.next();
        } catch (Exception ignored) {
            // 中断/resume 后可能取不到状态，按“继续执行”处理，避免任务永远卡在“运行中”
        }
        boolean waitingHuman = DataAgentSpec.Graph.Node.INTERRUPT_NODE.equals(next);
        traceService.endTask(threadId, waitingHuman ? "等待人工输入" : "完成");
        if (waitingHuman) {
            taskUpdater.requiresInput();
        } else {
            // 正常完成：必须调用 complete() 发出终态(COMPLETED)并关闭流，
            // 否则前端收不到 status-update.completed，会一直卡在“处理中”
            taskUpdater.complete();
        }
    }
}
