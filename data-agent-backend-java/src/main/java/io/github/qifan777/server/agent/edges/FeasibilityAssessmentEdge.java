package io.github.qifan777.server.agent.edges;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import io.github.qifan777.server.agent.DataAgentSpec;

public class FeasibilityAssessmentEdge implements EdgeAction {
    @Override
    public String apply(OverAllState state) {
        String output = state.value(DataAgentSpec.Graph.StateKey.Execution.FEASIBILITY_RESULT, "");
        // 数据分析类问题 -> 进入规划器，继续后续流程
        if (output.contains("数据分析") && !output.contains("需要澄清")) {
            return DataAgentSpec.Graph.Node.PLANNER;
        }
        // 需要澄清/口径不明确 -> 不直接结束，把澄清问题抛给用户追问，用户回答后继续
        if (output.contains("需要澄清") || output.contains("需求澄清")
                || output.contains("请澄清") || output.contains("无法确定口径")) {
            return DataAgentSpec.Graph.Node.REPORT_GENERATION;
        }
        // 其余（业务咨询/问候等）-> 结束
        return StateGraph.END;
    }
}
