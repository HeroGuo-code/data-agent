package io.github.qifan777.server.agent.edges;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import io.github.qifan777.server.agent.DataAgentSpec;

public class PlanExecutorEdge implements EdgeAction {
    @Override
    public String apply(OverAllState state) {
        String nextNode = state.value(DataAgentSpec.Graph.StateKey.Planning.NEXT_NODE, StateGraph.END);
        return nextNode;
    }
}
