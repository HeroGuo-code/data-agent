package io.github.qifan777.server.agent;

import com.alibaba.cloud.ai.graph.OverAllState;

/** 常量定义，对应原 Kotlin 的 DataAgentSpec object。 */
public final class DataAgentSpec {
    public static final String GRAPH_NAME = "data-agent-main-graph";

    private DataAgentSpec() {}

    public static final class Graph {
        private Graph() {}
        public static final class Node {
            private Node() {}
            public static final String INTENT_RECOGNITION = "INTENT_RECOGNITION_NODE";
            public static final String EVIDENCE_RECALL = "EVIDENCE_RECALL_NODE";
            public static final String SCHEMA_RECALL = "SCHEME_RECALL_NODE";
            public static final String TABLE_RELATION = "TABLE_RELATION_NODE";
            public static final String FEASIBILITY_ASSESSMENT = "FEASIBILITY_ASSESSMENT_NODE";
            public static final String PLANNER = "PLANNER_NODE";
            public static final String HUMAN_FEEDBACK = "HUMAN_FEEDBACK_NODE";
            public static final String PLAN_EXECUTION = "PLAN_EXECUTE_NODE";
            public static final String SQL_GENERATION = "SQL_GENERATE_NODE";
            public static final String SQL_EXECUTION = "SQL_EXECUTE_NODE";
            public static final String PYTHON_GENERATION = "PYTHON_GENERATE_NODE";
            public static final String PYTHON_EXECUTION = "PYTHON_EXECUTE_NODE";
            public static final String PYTHON_ANALYSIS = "PYTHON_ANALYZE_NODE";
            public static final String REPORT_GENERATION = "REPORT_GENERATOR_NODE";
            public static final String PROMPT_OUTPUT = "PROMPT_OUTPUT_NODE";
            public static final String INTERRUPT_NODE = HUMAN_FEEDBACK;
        }
        public static final class StateKey {
            private StateKey() {}
            public static final class Input {
                private Input() {}
                public static final String USER_INPUT = "input";
                public static final String DATABASE_ID = "databaseId";
                public static final String MULTI_TURN_CONTEXT = "MULTI_TURN_CONTEXT";
                public static final String TRACE_TASK_ID = "traceTaskId";
                public static final String ROLE = "role";
                public static final String HUMAN_IN_THE_LOOP = "HUMAN_IN_THE_LOOP";
                public static final String ANSWER_MODE = "ANSWER_MODE";
            }
            public static final class Recall {
                private Recall() {}
                public static final String REWRITE_QUERY = "REWRITE_QUERY";
                public static final String EVIDENCE = "EVIDENCE";
                public static final String COLUMN_SCHEMA = "COLUMN_SCHEME";
                public static final String TABLE_SCHEMA = "TABLE_SCHEME";
                public static final String TABLE_RELATION = "TABLE_RELATION_OUTPUT";
            }
            public static final class Planning {
                private Planning() {}
                public static final String PLAN = "PLANNER_NODE_OUTPUT";
                public static final String REPAIR_COUNT = "PLAN_REPAIR_COUNT";
                public static final String NEXT_NODE = "PLAN_NEXT_NODE";
                public static final String CURRENT_STEP = "PLAN_CURRENT_STEP";
                public static final String EXECUTION_OUTPUT = "PLAN_EXECUTE_NODE_OUTPUT";
            }
            public static final class Intent {
                private Intent() {}
                public static final String CLASSIFICATION = "INTENT_CLASSIFICATION";
                public static final String TYPE = "INTENT_TYPE";
                public static final String GREETING = "greeting";
                public static final String CHAT = "chat";
                public static final String DATA = "data";
            }
            public static final class HumanReview {
                private HumanReview() {}
                public static final String CONFIRMATION_APPROVED = "confirmationApproved";
                public static final String CONFIRMATION_FEEDBACK = "confirmationFeedback";
                public static final String NEXT_NODE = "HUMAN_NEXT_NODE";
            }
            public static final class Execution {
                private Execution() {}
                public static final String FEASIBILITY_RESULT = "FEASIBILITY_ASSESSMENT_NODE_OUTPUT";
                public static final String SQL_GENERATION_RESULT = "SQL_GENERATE_OUTPUT";
                public static final String SQL_EXECUTION_RESULT = "SQL_EXECUTE_OUTPUT";
                public static final String PYTHON_GENERATION_RESULT = "PYTHON_GENERATE_NODE_OUTPUT";
                public static final String PYTHON_EXECUTION_RESULT = "PYTHON_EXECUTE_NODE_OUTPUT";
                public static final String REPORT_RESULT = "REPORT_GENERATOR_NODE_OUTPUT";
            }
        }
    }

    public static final class MessageMetadataKey {
        private MessageMetadataKey() {}
        public static final String DATABASE_ID = "databaseId";
        public static final String CONFIRMATION_APPROVED = "confirmationApproved";
        public static final String CONFIRMATION_FEEDBACK = "confirmationFeedback";
        public static final String HUMAN_IN_THE_LOOP = "humanInTheLoop";
        public static final String ANSWER_MODE = "answerMode";
        public static final String MULTI_TURN_CONTEXT = "multiTurnContext";
    }

    public static final class Retrieval {
        private Retrieval() {}
        public static final class DocumentMetadataKey {
            private DocumentMetadataKey() {}
            public static final String TABLE_ID = "tableId";
            public static final String COLUMN_ID = "columnId";
            public static final String KNOWLEDGE_ID = "knowledgeId";
            public static final String DATABASE_ID = "databaseId";
            public static final String BUSINESS_TERM_ID = "businessTermId";
            public static final String VECTOR_TYPE = "vectorType";
        }
        public static final class VectorType {
            private VectorType() {}
            public static final String QUESTION_KNOWLEDGE = "questionKnowledge";
            public static final String GLOSSARY_KNOWLEDGE = "glossaryKnowledge";
            public static final String COLUMN = "column";
            public static final String TABLE = "table";
        }
    }

    public static final class PromptName {
        private PromptName() {}
        public static final String INTENT_RECOGNITION = "intent-recognition";
        public static final String EVIDENCE_QUERY_REWRITE = "evidence-query-rewrite";
        public static final String AGENT_KNOWLEDGE = "agent-knowledge";
        public static final String QUERY_ENHANCEMENT = "query-enhancement";
        public static final String FEASIBILITY_ASSESSMENT = "feasibility-assessment";
        public static final String MIX_SELECTOR = "mix-selector";
        public static final String SEMANTIC_CONSISTENCY = "semantic-consistency";
        public static final String SQL_GENERATION = "new-sql-generate";
        public static final String PLANNER = "planner";
        public static final String REPORT_GENERATION = "report-generator-plain";
        public static final String SQL_ERROR_FIXER = "sql-error-fixer";
        public static final String PYTHON_GENERATION = "python-generator";
        public static final String PYTHON_ANALYZE = "python-analyze";
        public static final String BUSINESS_KNOWLEDGE = "business-knowledge";
        public static final String SEMANTIC_MODEL = "semantic-model";
        public static final String DATA_VIEW_ANALYZE = "data-view-analyze";
    }
}
