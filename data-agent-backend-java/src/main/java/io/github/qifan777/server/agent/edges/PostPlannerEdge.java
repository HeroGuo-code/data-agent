package io.github.qifan777.server.agent.edges;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import io.github.qifan777.server.agent.DataAgentSpec;

/**
 * 规划器后的条件路由：
 * 1. 前端“人工确认”开关打开时，无条件进入人工确认节点，必须人工介入；
 * 2. 开关关闭时，只有当可行性评估判定需求“需澄清 / 口径不明确”时才路由到人工节点
 *    （节点内部会跳过等待，直接放行），清晰问题直接进入计划执行。
 */
public class PostPlannerEdge implements EdgeAction {
    @Override
    public String apply(OverAllState state) {
        Boolean hitlEnabled = state.value(DataAgentSpec.Graph.StateKey.Input.HUMAN_IN_THE_LOOP, false);
        if (Boolean.TRUE.equals(hitlEnabled)) {
            return DataAgentSpec.Graph.Node.HUMAN_FEEDBACK;
        }
        String feasibility = state.value(DataAgentSpec.Graph.StateKey.Execution.FEASIBILITY_RESULT, "");
        boolean needClarify = feasibility.contains("需要澄清") || feasibility.contains("需求澄清")
            || feasibility.contains("请澄清") || feasibility.contains("无法确定口径")
            || feasibility.contains("需要补充") || feasibility.contains("需要确认")
            || feasibility.contains("口径不明确") || feasibility.contains("需求不明确");
        if (needClarify) {
            return DataAgentSpec.Graph.Node.HUMAN_FEEDBACK;
        }
        return DataAgentSpec.Graph.Node.PLAN_EXECUTION;
    }
}
