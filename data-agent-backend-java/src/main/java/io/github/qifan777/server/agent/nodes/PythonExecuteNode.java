package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.graph.nodes.SqlExecuteNode;
import io.github.qifan777.server.shared.json.JsonUtil;
import io.github.qifan777.server.shared.python.SimplePythonExecutor;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PythonExecuteNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) {
        String pythonCode = state.value(DataAgentSpec.Graph.StateKey.Execution.PYTHON_GENERATION_RESULT, "");
        SqlExecuteNode.Result result = state.value(DataAgentSpec.Graph.StateKey.Execution.SQL_EXECUTION_RESULT, SqlExecuteNode.Result.class).orElseThrow();
        String inputDataJson = JsonUtil.toJson(result.getResultSet().getData());
        if (inputDataJson == null) throw new RuntimeException("sql结果为空");
        Object output = SimplePythonExecutor.execute(pythonCode, inputDataJson);
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Execution.PYTHON_EXECUTION_RESULT, output);
        return out;
    }
}
