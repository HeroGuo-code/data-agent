package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.model.Plan;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PlanExecuteNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) {
        Plan plan = Plan.getPlan(state);
        Integer currentStep = state.value(DataAgentSpec.Graph.StateKey.Planning.CURRENT_STEP, 1);
        List<Plan.PlanStep> steps = plan.getExecutionPlan();
        Map<String, Object> out = new HashMap<>();
        if (currentStep > steps.size()) {
            out.put(DataAgentSpec.Graph.StateKey.Planning.CURRENT_STEP, 1);
            boolean lastIsReport = !steps.isEmpty()
                && DataAgentSpec.Graph.Node.REPORT_GENERATION.equals(steps.get(steps.size() - 1).getToolToUse());
            out.put(DataAgentSpec.Graph.StateKey.Planning.NEXT_NODE,
                lastIsReport ? StateGraph.END : DataAgentSpec.Graph.Node.REPORT_GENERATION);
            return out;
        }
        out.put(DataAgentSpec.Graph.StateKey.Planning.NEXT_NODE, steps.get(currentStep - 1).getToolToUse());
        return out;
    }
}
