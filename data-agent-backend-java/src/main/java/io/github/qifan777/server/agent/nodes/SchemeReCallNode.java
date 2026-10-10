package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.model.Plan;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.document.Document;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import org.springframework.stereotype.Component;

@Component
public class SchemeReCallNode implements NodeAction {
    private final VectorStore vectorStore;
    public SchemeReCallNode(VectorStore vectorStore) { this.vectorStore = vectorStore; }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String rewriteQuery = state.value(DataAgentSpec.Graph.StateKey.Recall.REWRITE_QUERY, "");
        String databaseId = state.value(DataAgentSpec.Graph.StateKey.Input.DATABASE_ID, "");
        List<Document> tableDocuments = retrieveTable(rewriteQuery, databaseId);
        List<Document> columnDocuments = retrieveColumn(rewriteQuery, databaseId);
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Recall.TABLE_SCHEMA, tableDocuments);
        out.put(DataAgentSpec.Graph.StateKey.Recall.COLUMN_SCHEMA, columnDocuments);
        return out;
    }

    private List<Document> retrieveTable(String question, String databaseId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var expr = b.and(
            b.eq(DataAgentSpec.Retrieval.DocumentMetadataKey.VECTOR_TYPE, DataAgentSpec.Retrieval.VectorType.TABLE),
            b.eq(DataAgentSpec.Retrieval.DocumentMetadataKey.DATABASE_ID, databaseId)).build();
        SearchRequest req = SearchRequest.builder().query(question).filterExpression(expr).topK(10).build();
        return vectorStore.similaritySearch(req);
    }

    private List<Document> retrieveColumn(String question, String databaseId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var expr = b.and(
            b.eq(DataAgentSpec.Retrieval.DocumentMetadataKey.VECTOR_TYPE, DataAgentSpec.Retrieval.VectorType.COLUMN),
            b.eq(DataAgentSpec.Retrieval.DocumentMetadataKey.DATABASE_ID, databaseId)).build();
        SearchRequest req = SearchRequest.builder().query(question).filterExpression(expr).topK(30).build();
        return vectorStore.similaritySearch(req);
    }
}
