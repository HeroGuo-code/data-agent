package io.github.qifan777.server.agent.edges;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import io.github.qifan777.server.agent.DataAgentSpec;

/**
 * 意图识别后的条件路由：
 * 问候/闲聊/无关请求进入提示词输出节点（按配置的开场白或人设直接回复）；
 * 可能的数据分析请求进入证据召回。
 */
public class IntentRecognitionEdge implements EdgeAction {
    @Override
    public String apply(OverAllState state) {
        String type = state.value(DataAgentSpec.Graph.StateKey.Intent.TYPE, "");
        if (DataAgentSpec.Graph.StateKey.Intent.DATA.equals(type)) {
            return DataAgentSpec.Graph.Node.EVIDENCE_RECALL;
        }
        return DataAgentSpec.Graph.Node.PROMPT_OUTPUT;
    }
}
