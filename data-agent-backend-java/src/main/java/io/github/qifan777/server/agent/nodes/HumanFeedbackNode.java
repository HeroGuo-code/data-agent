package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HumanFeedbackNode implements NodeAction {
    @Override
    public Map<String, Object> apply(OverAllState state) {
        Boolean hitlEnabled = state.value(DataAgentSpec.Graph.StateKey.Input.HUMAN_IN_THE_LOOP, true);
        if (!Boolean.TRUE.equals(hitlEnabled)) {
            Map<String, Object> skip = new HashMap<>();
            skip.put(DataAgentSpec.Graph.StateKey.HumanReview.NEXT_NODE, DataAgentSpec.Graph.Node.PLAN_EXECUTION);
            return skip;
        }
        Integer count = state.value(DataAgentSpec.Graph.StateKey.Planning.REPAIR_COUNT, 1);
        if (count >= 3) {
            Map<String, Object> out = new HashMap<>();
            out.put(DataAgentSpec.Graph.StateKey.HumanReview.NEXT_NODE, StateGraph.END);
            return out;
        }
        Boolean approved = state.value(DataAgentSpec.Graph.StateKey.HumanReview.CONFIRMATION_APPROVED, false);
        Map<String, Object> out = new HashMap<>();
        if (Boolean.TRUE.equals(approved)) {
            out.put(DataAgentSpec.Graph.StateKey.HumanReview.NEXT_NODE, DataAgentSpec.Graph.Node.PLAN_EXECUTION);
        } else {
            String feedbackContent = state.value(DataAgentSpec.Graph.StateKey.HumanReview.CONFIRMATION_FEEDBACK, "");
            out.put(DataAgentSpec.Graph.StateKey.HumanReview.NEXT_NODE, DataAgentSpec.Graph.Node.PLANNER);
            out.put(DataAgentSpec.Graph.StateKey.Planning.REPAIR_COUNT, count + 1);
            out.put(DataAgentSpec.Graph.StateKey.HumanReview.CONFIRMATION_FEEDBACK,
                feedbackContent.isEmpty() ? "Plan rejected by user" : feedbackContent);
        }
        return out;
    }
}
