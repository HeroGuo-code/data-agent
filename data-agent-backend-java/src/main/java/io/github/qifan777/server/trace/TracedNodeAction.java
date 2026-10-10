package io.github.qifan777.server.trace;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.qifan777.server.agent.DataAgentSpec;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** 图节点执行追踪包装：记录每个节点的开始/耗时/状态/输入输出，构建调用树与火焰图。 */
public class TracedNodeAction implements AsyncNodeAction {

    private static final int MAX_DETAIL = 4000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AsyncNodeAction delegate;
    private final String nodeName;
    private final TaskTraceService traceService;

    public TracedNodeAction(AsyncNodeAction delegate, String nodeName, TaskTraceService traceService) {
        this.delegate = delegate;
        this.nodeName = nodeName;
        this.traceService = traceService;
    }

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        String taskId = state.value(DataAgentSpec.Graph.StateKey.Input.TRACE_TASK_ID, "unknown");
        String question = state.value(DataAgentSpec.Graph.StateKey.Input.USER_INPUT, "");
        String role = state.value(DataAgentSpec.Graph.StateKey.Input.ROLE, PermissionName.ROLE_OPERATOR);
        traceService.beginTask(taskId, question, role);
        TraceFrame f = traceService.start(taskId, nodeName, null);
        f.setInputDetail(summarize(state.data()));
        return delegate.apply(state).handle((re, err) -> {
            if (err != null) {
                traceService.finish(f, "error");
                f.setOutputDetail("执行异常：" + (err.getMessage() == null ? String.valueOf(err) : err.getMessage()));
                return null;
            }
            traceService.finish(f, "success");
            f.setOutputDetail(summarize(re));
            return re;
        });
    }

    private static String summarize(Object value) {
        if (value == null) return "(空)";
        try {
            String s = MAPPER.writeValueAsString(value);
            if (s.length() > MAX_DETAIL) return s.substring(0, MAX_DETAIL) + "\n...(已截断，共 " + s.length() + " 字符)";
            return s;
        } catch (Exception e) {
            String s = String.valueOf(value);
            return s.length() > MAX_DETAIL ? s.substring(0, MAX_DETAIL) + "..." : s;
        }
    }

    /** 角色常量（避免与 security 包硬耦合）。 */
    public static final class PermissionName {
        public static final String ROLE_OPERATOR = "运营";
        public static final String ROLE_AGENT = "客服坐席";
        public static final String ROLE_ADMIN = "管理员";
    }
}
