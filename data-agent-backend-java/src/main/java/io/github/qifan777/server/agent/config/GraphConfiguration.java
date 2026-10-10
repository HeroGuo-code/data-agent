package io.github.qifan777.server.agent.config;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.edges.FeasibilityAssessmentEdge;
import io.github.qifan777.server.agent.edges.HumanFeedbackEdge;
import io.github.qifan777.server.agent.edges.IntentRecognitionEdge;
import io.github.qifan777.server.agent.edges.PostPlannerEdge;
import io.github.qifan777.server.agent.edges.PlanExecutorEdge;
import io.github.qifan777.server.agent.nodes.EvidenceRecallNode;
import io.github.qifan777.server.agent.nodes.FeasibilityAssessmentNode;
import io.github.qifan777.server.agent.nodes.HumanFeedbackNode;
import io.github.qifan777.server.agent.nodes.IntentRecognitionNode;
import io.github.qifan777.server.agent.nodes.PlanExecuteNode;
import io.github.qifan777.server.agent.nodes.PlannerNode;
import io.github.qifan777.server.agent.nodes.PromptOutputNode;
import io.github.qifan777.server.agent.nodes.PythonAnalyzeNode;
import io.github.qifan777.server.agent.nodes.PythonExecuteNode;
import io.github.qifan777.server.agent.nodes.PythonGeneratorNode;
import io.github.qifan777.server.agent.nodes.ReportGeneratorNode;
import io.github.qifan777.server.agent.nodes.SchemeReCallNode;
import io.github.qifan777.server.agent.nodes.SqlGeneratorNode;
import io.github.qifan777.server.agent.nodes.TableRelationNode;
import io.github.qifan777.server.graph.nodes.SqlExecuteNode;
import io.github.qifan777.server.trace.TaskTraceService;
import io.github.qifan777.server.trace.TracedNodeAction;
import java.util.HashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GraphConfiguration {

    @Bean
    public SpringAIJacksonStateSerializer serializer() {
        SpringAIJacksonStateSerializer serializer = new SpringAIJacksonStateSerializer(OverAllState::new);
        serializer.objectMapper().registerModules(new JavaTimeModule());
        return serializer;
    }

    @Bean
    public StateGraph graph(EvidenceRecallNode evidenceRecallNode,
                            SchemeReCallNode schemeReCallNode,
                            TableRelationNode tableRelationNode,
                            FeasibilityAssessmentNode feasibilityAssessmentNode,
                            IntentRecognitionNode intentRecognitionNode,
                            PlannerNode plannerNode,
                            HumanFeedbackNode humanFeedbackNode,
                            PlanExecuteNode planExecuteNode,
                            SqlGeneratorNode sqlGeneratorNode,
                            SqlExecuteNode sqlExecuteNode,
                            PythonGeneratorNode pythonGeneratorNode,
                            PythonExecuteNode pythonExecuteNode,
                            PythonAnalyzeNode pythonAnalyzeNode,
                            ReportGeneratorNode reportGeneratorNode,
                            PromptOutputNode promptOutputNode,
                            StateSerializer serializer,
                            TaskTraceService taskTraceService) {
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> map = new HashMap<>();
            map.put(DataAgentSpec.Graph.StateKey.Input.USER_INPUT, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Input.DATABASE_ID, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Input.MULTI_TURN_CONTEXT, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Input.TRACE_TASK_ID, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Input.ROLE, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Input.HUMAN_IN_THE_LOOP, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Input.ANSWER_MODE, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Recall.REWRITE_QUERY, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Recall.EVIDENCE, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Recall.TABLE_SCHEMA, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Recall.COLUMN_SCHEMA, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Recall.TABLE_RELATION, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Planning.PLAN, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Planning.REPAIR_COUNT, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Planning.NEXT_NODE, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Planning.CURRENT_STEP, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Planning.EXECUTION_OUTPUT, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.HumanReview.CONFIRMATION_APPROVED, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.HumanReview.CONFIRMATION_FEEDBACK, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.HumanReview.NEXT_NODE, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Execution.FEASIBILITY_RESULT, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Intent.CLASSIFICATION, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Execution.SQL_GENERATION_RESULT, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Execution.SQL_EXECUTION_RESULT, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Execution.PYTHON_GENERATION_RESULT, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Execution.PYTHON_EXECUTION_RESULT, new ReplaceStrategy());
            map.put(DataAgentSpec.Graph.StateKey.Execution.REPORT_RESULT, new ReplaceStrategy());
            return map;
        };
        try {
            StateGraph sg = new StateGraph(DataAgentSpec.GRAPH_NAME, keyStrategyFactory, serializer);
            return sg.addNode(DataAgentSpec.Graph.Node.INTENT_RECOGNITION, new TracedNodeAction(AsyncNodeAction.node_async(intentRecognitionNode), DataAgentSpec.Graph.Node.INTENT_RECOGNITION, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.EVIDENCE_RECALL, new TracedNodeAction(AsyncNodeAction.node_async(evidenceRecallNode), DataAgentSpec.Graph.Node.EVIDENCE_RECALL, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.SCHEMA_RECALL, new TracedNodeAction(AsyncNodeAction.node_async(schemeReCallNode), DataAgentSpec.Graph.Node.SCHEMA_RECALL, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.TABLE_RELATION, new TracedNodeAction(AsyncNodeAction.node_async(tableRelationNode), DataAgentSpec.Graph.Node.TABLE_RELATION, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.FEASIBILITY_ASSESSMENT, new TracedNodeAction(AsyncNodeAction.node_async(feasibilityAssessmentNode), DataAgentSpec.Graph.Node.FEASIBILITY_ASSESSMENT, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.PLANNER, new TracedNodeAction(AsyncNodeAction.node_async(plannerNode), DataAgentSpec.Graph.Node.PLANNER, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.HUMAN_FEEDBACK, new TracedNodeAction(AsyncNodeAction.node_async(humanFeedbackNode), DataAgentSpec.Graph.Node.HUMAN_FEEDBACK, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.PLAN_EXECUTION, new TracedNodeAction(AsyncNodeAction.node_async(planExecuteNode), DataAgentSpec.Graph.Node.PLAN_EXECUTION, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.SQL_GENERATION, new TracedNodeAction(AsyncNodeAction.node_async(sqlGeneratorNode), DataAgentSpec.Graph.Node.SQL_GENERATION, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.SQL_EXECUTION, new TracedNodeAction(AsyncNodeAction.node_async(sqlExecuteNode), DataAgentSpec.Graph.Node.SQL_EXECUTION, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.PYTHON_GENERATION, new TracedNodeAction(AsyncNodeAction.node_async(pythonGeneratorNode), DataAgentSpec.Graph.Node.PYTHON_GENERATION, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.PYTHON_EXECUTION, new TracedNodeAction(AsyncNodeAction.node_async(pythonExecuteNode), DataAgentSpec.Graph.Node.PYTHON_EXECUTION, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.PYTHON_ANALYSIS, new TracedNodeAction(AsyncNodeAction.node_async(pythonAnalyzeNode), DataAgentSpec.Graph.Node.PYTHON_ANALYSIS, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.REPORT_GENERATION, new TracedNodeAction(AsyncNodeAction.node_async(reportGeneratorNode), DataAgentSpec.Graph.Node.REPORT_GENERATION, taskTraceService))
                .addNode(DataAgentSpec.Graph.Node.PROMPT_OUTPUT, new TracedNodeAction(AsyncNodeAction.node_async(promptOutputNode), DataAgentSpec.Graph.Node.PROMPT_OUTPUT, taskTraceService))
                .addEdge(StateGraph.START, DataAgentSpec.Graph.Node.INTENT_RECOGNITION)
                .addConditionalEdges(DataAgentSpec.Graph.Node.INTENT_RECOGNITION,
                    AsyncEdgeAction.edge_async(new IntentRecognitionEdge()),
                    Map.of(DataAgentSpec.Graph.Node.EVIDENCE_RECALL, DataAgentSpec.Graph.Node.EVIDENCE_RECALL,
                        DataAgentSpec.Graph.Node.PROMPT_OUTPUT, DataAgentSpec.Graph.Node.PROMPT_OUTPUT))
                .addEdge(DataAgentSpec.Graph.Node.EVIDENCE_RECALL, DataAgentSpec.Graph.Node.SCHEMA_RECALL)
                .addEdge(DataAgentSpec.Graph.Node.SCHEMA_RECALL, DataAgentSpec.Graph.Node.TABLE_RELATION)
                .addEdge(DataAgentSpec.Graph.Node.TABLE_RELATION, DataAgentSpec.Graph.Node.FEASIBILITY_ASSESSMENT)
                .addConditionalEdges(DataAgentSpec.Graph.Node.FEASIBILITY_ASSESSMENT,
                    AsyncEdgeAction.edge_async(new FeasibilityAssessmentEdge()),
                    Map.of(DataAgentSpec.Graph.Node.PLANNER, DataAgentSpec.Graph.Node.PLANNER,
                        DataAgentSpec.Graph.Node.REPORT_GENERATION, DataAgentSpec.Graph.Node.REPORT_GENERATION,
                        StateGraph.END, StateGraph.END))
                .addConditionalEdges(DataAgentSpec.Graph.Node.PLANNER,
                    AsyncEdgeAction.edge_async(new PostPlannerEdge()),
                    new HashMap<String,String>() {{
                        put(DataAgentSpec.Graph.Node.PLAN_EXECUTION, DataAgentSpec.Graph.Node.PLAN_EXECUTION);
                        put(DataAgentSpec.Graph.Node.HUMAN_FEEDBACK, DataAgentSpec.Graph.Node.HUMAN_FEEDBACK);
                    }})
                .addConditionalEdges(DataAgentSpec.Graph.Node.HUMAN_FEEDBACK,
                    AsyncEdgeAction.edge_async(new HumanFeedbackEdge()),
                    new HashMap<String,String>() {{
                        put(StateGraph.END, StateGraph.END);
                        put(DataAgentSpec.Graph.Node.PLAN_EXECUTION, DataAgentSpec.Graph.Node.PLAN_EXECUTION);
                        put(DataAgentSpec.Graph.Node.PLANNER, DataAgentSpec.Graph.Node.PLANNER);
                    }})
                .addConditionalEdges(DataAgentSpec.Graph.Node.PLAN_EXECUTION,
                    AsyncEdgeAction.edge_async(new PlanExecutorEdge()),
                    new HashMap<String,String>() {{
                        put(DataAgentSpec.Graph.Node.SQL_GENERATION, DataAgentSpec.Graph.Node.SQL_GENERATION);
                        put(DataAgentSpec.Graph.Node.PYTHON_GENERATION, DataAgentSpec.Graph.Node.PYTHON_GENERATION);
                        put(DataAgentSpec.Graph.Node.REPORT_GENERATION, DataAgentSpec.Graph.Node.REPORT_GENERATION);
                        put(StateGraph.END, StateGraph.END);
                    }})
                .addEdge(DataAgentSpec.Graph.Node.SQL_GENERATION, DataAgentSpec.Graph.Node.SQL_EXECUTION)
                .addEdge(DataAgentSpec.Graph.Node.SQL_EXECUTION, DataAgentSpec.Graph.Node.PLAN_EXECUTION)
                .addEdge(DataAgentSpec.Graph.Node.PYTHON_GENERATION, DataAgentSpec.Graph.Node.PYTHON_EXECUTION)
                .addEdge(DataAgentSpec.Graph.Node.PYTHON_EXECUTION, DataAgentSpec.Graph.Node.PYTHON_ANALYSIS)
                .addEdge(DataAgentSpec.Graph.Node.PYTHON_ANALYSIS, DataAgentSpec.Graph.Node.PLAN_EXECUTION)
                .addEdge(DataAgentSpec.Graph.Node.REPORT_GENERATION, StateGraph.END)
                .addEdge(DataAgentSpec.Graph.Node.PROMPT_OUTPUT, StateGraph.END);
        } catch (GraphStateException e) {
            throw new RuntimeException(e);
        }
    }
}
