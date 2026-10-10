package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.DocumentExtensions;
import io.github.qifan777.server.agent.model.EvidenceQueryRewriteResult;
import io.github.qifan777.server.agent.prompt.PromptManager;
import io.github.qifan777.server.dataset.knowledge.domain.QuestionKnowledge;
import io.github.qifan777.server.dataset.knowledge.mapper.QuestionKnowledgeMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

@Component
public class EvidenceRecallNode implements NodeAction {
    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final QuestionKnowledgeMapper questionKnowledgeMapper;
    private final PromptManager promptManager;

    public EvidenceRecallNode(ChatModel chatModel, VectorStore vectorStore,
                              QuestionKnowledgeMapper questionKnowledgeMapper, PromptManager promptManager) {
        this.chatModel = chatModel; this.vectorStore = vectorStore;
        this.questionKnowledgeMapper = questionKnowledgeMapper; this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) {
        String userInput = state.value(DataAgentSpec.Graph.StateKey.Input.USER_INPUT, "");
        String databaseId = state.value(DataAgentSpec.Graph.StateKey.Input.DATABASE_ID, "");
        String multiTurn = state.value(DataAgentSpec.Graph.StateKey.Input.MULTI_TURN_CONTEXT, "(无)");
        BeanOutputConverter<EvidenceQueryRewriteResult> beanOutputConverter = new BeanOutputConverter<>(EvidenceQueryRewriteResult.class);
        Map<String, Object> args = new HashMap<>();
        args.put("latest_query", userInput);
        args.put("format", beanOutputConverter.getFormat());
        args.put("multi_turn", multiTurn);
        String rewritePrompt = promptManager.getEvidenceQueryRewritePromptTemplate().render(args);
        String rewriteResponse = ChatClient.create(chatModel)
            .prompt()
            .options(OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false)).build())
            .user(rewritePrompt)
            .call()
            .content();
        if (rewriteResponse == null) throw new IllegalArgumentException("Invalid rewrite response");
        EvidenceQueryRewriteResult convert = new BeanOutputConverter<EvidenceQueryRewriteResult>(EvidenceQueryRewriteResult.class).convert(rewriteResponse);
        if (convert == null) throw new IllegalArgumentException("Invalid rewrite response");
        String rewriteQuery = convert.getStandaloneQuery();
        List<Document> terms = retrieveGlossaryKnowledge(rewriteQuery, databaseId);
        List<Document> knowledgeDocs = retrieveKnowledge(rewriteQuery, databaseId);
        StringBuilder glossaryKnowledgeText = new StringBuilder();
        for (Document d : terms) {
            if (glossaryKnowledgeText.length() > 0) glossaryKnowledgeText.append("\n");
            glossaryKnowledgeText.append(d.getText());
        }
        Set<UUID> idSet = new LinkedHashSet<>();
        for (Document d : knowledgeDocs) {
            UUID id = DocumentExtensions.uuidOrNull(d.getMetadata(), DataAgentSpec.Retrieval.DocumentMetadataKey.KNOWLEDGE_ID);
            if (id != null) idSet.add(id);
        }
        int invalidCount = knowledgeDocs.size() - idSet.size();
        List<UUID> ids = new ArrayList<>(idSet);
        List<QuestionKnowledge> qkList = ids.isEmpty() ? new ArrayList<>() : questionKnowledgeMapper.findByIds(ids.stream().map(UUID::toString).toList());
        StringBuilder questionKnowledgeText = new StringBuilder();
        for (QuestionKnowledge qk : qkList) {
            if (questionKnowledgeText.length() > 0) questionKnowledgeText.append("\n");
            questionKnowledgeText.append("来源:").append(qk.getDatabaseId()).append(" Q: ").append(qk.getQuestion())
                .append(" A: ").append(qk.getAnswer());
        }
        String glossaryText = glossaryKnowledgeText.toString();
        String qkText = questionKnowledgeText.toString();
        String glossaryPrompt = promptManager.getBusinessKnowledgePromptTemplate()
            .render(Map.of("businessKnowledge", glossaryText.isEmpty() ? "无" : glossaryText));
        String knowledgePrompt = promptManager.getAgentKnowledgePromptTemplate()
            .render(Map.of("agentKnowledge", qkText.isEmpty() ? "无" : qkText));
        Map<String, Object> out = new HashMap<>();
        String evidence = (qkText.isEmpty() && glossaryText.isEmpty()) ? "无" : (glossaryPrompt + "\n" + knowledgePrompt);
        out.put(DataAgentSpec.Graph.StateKey.Recall.EVIDENCE, evidence);
        out.put(DataAgentSpec.Graph.StateKey.Recall.REWRITE_QUERY, rewriteQuery);
        return out;
    }

    private List<Document> retrieveGlossaryKnowledge(String question, String databaseId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var expr = b.and(
            b.eq(DataAgentSpec.Retrieval.DocumentMetadataKey.VECTOR_TYPE, DataAgentSpec.Retrieval.VectorType.GLOSSARY_KNOWLEDGE),
            b.eq(DataAgentSpec.Retrieval.DocumentMetadataKey.DATABASE_ID, databaseId)).build();
        SearchRequest req = SearchRequest.builder().query(question).filterExpression(expr).topK(4).build();
        return vectorStore.similaritySearch(req);
    }

    private List<Document> retrieveKnowledge(String question, String databaseId) {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        var expr = b.and(
            b.eq(DataAgentSpec.Retrieval.DocumentMetadataKey.VECTOR_TYPE, DataAgentSpec.Retrieval.VectorType.QUESTION_KNOWLEDGE),
            b.eq(DataAgentSpec.Retrieval.DocumentMetadataKey.DATABASE_ID, databaseId)).build();
        SearchRequest req = SearchRequest.builder().query(question).filterExpression(expr).topK(4).build();
        return vectorStore.similaritySearch(req);
    }
}
