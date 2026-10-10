package io.github.qifan777.server.graph.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.model.DisplaySpec;
import io.github.qifan777.server.agent.model.Plan;
import io.github.qifan777.server.agent.model.SqlResultSet;
import io.github.qifan777.server.security.PermissionInterceptor;
import io.github.qifan777.server.security.SqlSecurityValidator;
import io.github.qifan777.server.shared.datasource.ResultSetBuilder;
import io.github.qifan777.server.shared.datasource.SqliteSchemaDataSourceProvider;
import io.github.qifan777.server.shared.json.JsonUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SqlExecuteNode implements NodeAction {

    private static final Logger log = LoggerFactory.getLogger(SqlExecuteNode.class);

    private final PermissionInterceptor permissionInterceptor;

    public SqlExecuteNode(PermissionInterceptor permissionInterceptor) {
        this.permissionInterceptor = permissionInterceptor;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        Integer currentStep = state.value(DataAgentSpec.Graph.StateKey.Planning.CURRENT_STEP, 1);
        String sql = state.value(DataAgentSpec.Graph.StateKey.Execution.SQL_GENERATION_RESULT, "");
        String databaseId = state.value(DataAgentSpec.Graph.StateKey.Input.DATABASE_ID, "");

        // ---------- SQL ??????? SELECT ?????----------
        SqlSecurityValidator.Result sec = SqlSecurityValidator.validate(sql);
        if (!sec.allowed) {
            log.warn("[SQL_SECURITY] databaseId={} ?? sql={} reason={}", databaseId, sql, sec.reason);
            return failure(currentStep, "[????] " + sec.reason);
        }

        // ---------- ?? SQL?????????????????----------
        SqlResultSet resultSetWrapper;
        try (Connection conn = SqliteSchemaDataSourceProvider.INSTANCE.get(databaseId).getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            resultSetWrapper = ResultSetBuilder.buildFrom(rs);
        } catch (Exception e) {
            log.error("[SQL_EXECUTE] databaseId={} sql={} error={}", databaseId, sql, e.getMessage(), e);
            return failure(currentStep, "SQL ????: " + e.getMessage());
        }

        // ---------- ??????????????----------
        resultSetWrapper = permissionInterceptor.mask(resultSetWrapper);

        DisplaySpec displaySpec = buildDisplaySpec(resultSetWrapper);
        Plan.getCurrentStep(state).getToolParameters().setSqlQuery(sql);
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Planning.CURRENT_STEP, currentStep + 1);
        out.put(DataAgentSpec.Graph.StateKey.Execution.SQL_EXECUTION_RESULT, new Result(resultSetWrapper, displaySpec));
        Map<String, String> executionOutput = new HashMap<>();
        executionOutput.put("step_" + currentStep, JsonUtil.toJson(resultSetWrapper));
        out.put(DataAgentSpec.Graph.StateKey.Planning.EXECUTION_OUTPUT, executionOutput);
        return out;
    }

    private Map<String, Object> failure(Integer currentStep, String message) {
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Planning.CURRENT_STEP, currentStep + 1);
        Map<String, String> executionOutput = new HashMap<>();
        executionOutput.put("step_" + currentStep, JsonUtil.toJson(Map.of(
            "error", message,
            "status", "failed")));
        out.put(DataAgentSpec.Graph.StateKey.Planning.EXECUTION_OUTPUT, executionOutput);
        return out;
    }

    private DisplaySpec buildDisplaySpec(SqlResultSet resultSetWrapper) {
        if (resultSetWrapper.getColumn().isEmpty()) {
            return new DisplaySpec("table", "SQL??????????", null, new ArrayList<>());
        }
        String xAxis = resultSetWrapper.getColumn().get(0);
        List<String> yAxis = new ArrayList<>(resultSetWrapper.getColumn().subList(1, resultSetWrapper.getColumn().size()));
        return new DisplaySpec("table", "SQL????", xAxis, yAxis);
    }

    public static class Result {
        private SqlResultSet resultSet;
        private DisplaySpec display;

        public Result() {}
        public Result(SqlResultSet resultSet, DisplaySpec display) { this.resultSet = resultSet; this.display = display; }

        public SqlResultSet getResultSet() { return resultSet; }
        public void setResultSet(SqlResultSet resultSet) { this.resultSet = resultSet; }
        public DisplaySpec getDisplay() { return display; }
        public void setDisplay(DisplaySpec display) { this.display = display; }
    }
}
