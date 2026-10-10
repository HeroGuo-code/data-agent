package io.github.qifan777.server.agent.nodes;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.qifan777.server.agent.DataAgentSpec;
import io.github.qifan777.server.agent.DocumentExtensions;
import io.github.qifan777.server.agent.model.Schema;
import io.github.qifan777.server.agent.prompt.PromptManager;
import io.github.qifan777.server.dataset.scheme.domain.DbColumn;
import io.github.qifan777.server.dataset.scheme.domain.DbTable;
import io.github.qifan777.server.dataset.scheme.dto.DbForeignKeySchemaView;
import io.github.qifan777.server.dataset.scheme.dto.DbTableSchemaView;
import io.github.qifan777.server.dataset.scheme.dto.ForeignKeyProjection;
import io.github.qifan777.server.dataset.scheme.mapper.DbColumnMapper;
import io.github.qifan777.server.dataset.scheme.mapper.DbForeignKeyMapper;
import io.github.qifan777.server.dataset.scheme.mapper.DbTableMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
public class TableRelationNode implements NodeAction {
    private static final double baseHighSimilarityThreshold = 0.4;
    private static final int targetTableCount = 4;

    private final ObjectMapper objectMapper;
    private final ChatModel chatModel;
    private final DbForeignKeyMapper dbForeignKeyMapper;
    private final DbTableMapper dbTableMapper;
    private final DbColumnMapper dbColumnMapper;
    private final PromptManager promptManager;

    public TableRelationNode(ObjectMapper objectMapper, ChatModel chatModel,
                             DbForeignKeyMapper dbForeignKeyMapper, DbTableMapper dbTableMapper,
                             DbColumnMapper dbColumnMapper, PromptManager promptManager) {
        this.objectMapper = objectMapper; this.chatModel = chatModel;
        this.dbForeignKeyMapper = dbForeignKeyMapper; this.dbTableMapper = dbTableMapper;
        this.dbColumnMapper = dbColumnMapper; this.promptManager = promptManager;
    }

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String rewriteQuery = state.value(DataAgentSpec.Graph.StateKey.Recall.REWRITE_QUERY, "");
        String evidence = state.value(DataAgentSpec.Graph.StateKey.Recall.EVIDENCE, "");
        List<Document> tableDocuments = state.value(DataAgentSpec.Graph.StateKey.Recall.TABLE_SCHEMA, new ArrayList<Document>());
        List<Document> columnDocuments = state.value(DataAgentSpec.Graph.StateKey.Recall.COLUMN_SCHEMA, new ArrayList<Document>());
        String databaseId = state.value(DataAgentSpec.Graph.StateKey.Input.DATABASE_ID, "");

        Map<UUID, Double> tableScores = new HashMap<>();
        for (Document d : tableDocuments) {
            UUID tableId = DocumentExtensions.uuidOrNull(d.getMetadata(), DataAgentSpec.Retrieval.DocumentMetadataKey.TABLE_ID);
            Double score = d.getScore();
            if (tableId == null || score == null) continue;
            tableScores.merge(tableId, score, Math::max);
        }
        List<ColumnScore> columnScores = new ArrayList<>();
        for (Document d : columnDocuments) {
            UUID tableId = DocumentExtensions.uuidOrNull(d.getMetadata(), DataAgentSpec.Retrieval.DocumentMetadataKey.TABLE_ID);
            UUID columnId = DocumentExtensions.uuidOrNull(d.getMetadata(), DataAgentSpec.Retrieval.DocumentMetadataKey.COLUMN_ID);
            Double score = d.getScore();
            if (tableId == null || columnId == null || score == null) continue;
            columnScores.add(new ColumnScore(tableId, columnId, score));
        }
        ThresholdSelection thresholdSelection = selectThreshold(tableScores, columnScores);
        List<UUID> tableIds = thresholdSelection.tableIds;
        Map<UUID, Set<UUID>> highSimilarityColumnIdsByTable = thresholdSelection.highSimilarityColumnIdsByTable;

        List<ForeignKeyProjection> foreignKeys = dbForeignKeyMapper.findSchemaByDatabaseId(databaseId);
        List<ForeignKeyProjection> relatedForeignKeys = foreignKeys.stream()
            .filter(fk -> tableIds.contains(fk.getSourceTableId()) || tableIds.contains(fk.getTargetTableId()))
            .collect(Collectors.toList());
        Set<UUID> foreignKeyRelatedTableIds = new LinkedHashSet<>();
        for (ForeignKeyProjection fk : relatedForeignKeys) {
            foreignKeyRelatedTableIds.add(fk.getSourceTableId());
            foreignKeyRelatedTableIds.add(fk.getTargetTableId());
        }
        List<UUID> finalTableIds = new ArrayList<>(new LinkedHashSet<UUID>() {{
            addAll(tableIds); addAll(foreignKeyRelatedTableIds);
        }});

        Map<UUID, Set<UUID>> foreignKeyColumnIdsByTable = new HashMap<>();
        for (ForeignKeyProjection fk : relatedForeignKeys) {
            foreignKeyColumnIdsByTable.computeIfAbsent(fk.getSourceTableId(), k -> new HashSet<>()).add(fk.getSourceColumnId());
            foreignKeyColumnIdsByTable.computeIfAbsent(fk.getTargetTableId(), k -> new HashSet<>()).add(fk.getTargetColumnId());
        }

        Map<UUID, DbTable> allTables = new HashMap<>();
        for (DbTable t : dbTableMapper.findByDatabaseId(databaseId)) allTables.put(t.getId(), t);
        Map<UUID, List<DbColumn>> allColumns = new LinkedHashMap<>();
        for (DbColumn c : dbColumnMapper.findByDatabaseId(databaseId)) {
            allColumns.computeIfAbsent(c.getTableId(), k -> new ArrayList<>()).add(c);
        }

        List<DbTableSchemaView> tables = new ArrayList<>();
        for (UUID id : finalTableIds) {
            DbTable table = allTables.get(id);
            if (table == null) continue;
            List<DbColumn> cols = allColumns.getOrDefault(id, new ArrayList<>());
            Set<UUID> high = highSimilarityColumnIdsByTable.getOrDefault(id, new HashSet<>());
            Set<UUID> fkCols = foreignKeyColumnIdsByTable.getOrDefault(id, new HashSet<>());
            Set<UUID> selected = new HashSet<>(high); selected.addAll(fkCols);
            List<DbColumn> columns = selected.isEmpty() ? cols : cols.stream().filter(c -> selected.contains(c.getId())).collect(Collectors.toList());
            tables.add(toTableView(table, columns));
        }

        List<DbForeignKeySchemaView> fkViews = relatedForeignKeys.stream().map(TableRelationNode::toFkView).collect(Collectors.toList());
        String prompt = promptManager.getMixSelectorPromptTemplate()
            .render(Map.of("schema_info", new Schema(databaseId, tables, fkViews).buildSchemePrompt(),
                "question", rewriteQuery, "evidence", evidence));
        String filterResult = ChatClient.create(chatModel)
            .prompt()
            .options(OpenAiChatOptions.builder().extraBody(Map.of("enable_thinking", false)).build())
            .user(prompt)
            .call()
            .content();
        if (filterResult == null) throw new RuntimeException("mix select fail");
        List<String> filterTableNames = objectMapper.readValue(filterResult, new TypeReference<List<String>>() {});
        List<DbTable> filterTables = dbTableMapper.findByDatabaseIdAndNames(databaseId, filterTableNames);
        List<ForeignKeyProjection> filterForeignKeys = foreignKeys.stream()
            .filter(fk -> filterTableNames.contains(fk.getTargetTableName()) || filterTableNames.contains(fk.getSourceTableName()))
            .collect(Collectors.toList());
        List<DbTableSchemaView> filterTableViews = new ArrayList<>();
        Map<UUID,List<DbColumn>> colsByTable = new HashMap<>();
        for (DbColumn c : allColumns.values().stream().flatMap(List::stream).collect(Collectors.toList())) {
            colsByTable.computeIfAbsent(c.getTableId(), k -> new ArrayList<>()).add(c);
        }
        for (DbTable t : filterTables) filterTableViews.add(toTableView(t, colsByTable.getOrDefault(t.getId(), new ArrayList<>())));
        List<DbForeignKeySchemaView> filterFkViews = filterForeignKeys.stream().map(TableRelationNode::toFkView).collect(Collectors.toList());
        Schema finalSchema = new Schema(databaseId, filterTableViews, filterFkViews);
        Map<String, Object> out = new HashMap<>();
        out.put(DataAgentSpec.Graph.StateKey.Recall.TABLE_RELATION, finalSchema.toJson());
        return out;
    }

    private static DbTableSchemaView toTableView(DbTable t, List<DbColumn> columns) {
        DbTableSchemaView v = new DbTableSchemaView();
        v.setName(t.getName());
        List<DbTableSchemaView.ColumnView> cvs = new ArrayList<>();
        for (DbColumn c : columns) {
            DbTableSchemaView.ColumnView cv = new DbTableSchemaView.ColumnView();
            cv.setName(c.getName()); cv.setType(c.getType()); cv.setDescription(c.getDescription());
            cv.setPrimaryKey(c.isPrimaryKey());
            cvs.add(cv);
        }
        v.setColumns(cvs);
        return v;
    }

    private static DbForeignKeySchemaView toFkView(ForeignKeyProjection fk) {
        DbForeignKeySchemaView v = new DbForeignKeySchemaView();
        v.setSourceTableName(fk.getSourceTableName()); v.setSourceColumnName(fk.getSourceColumnName());
        v.setTargetTableName(fk.getTargetTableName()); v.setTargetColumnName(fk.getTargetColumnName());
        return v;
    }

    private ThresholdSelection selectThreshold(Map<UUID, Double> tableScores, List<ColumnScore> columnScores) {
        ThresholdSelection best = buildSelection(baseHighSimilarityThreshold, tableScores, columnScores);
        if (best.tableIds.size() <= targetTableCount) return best;
        for (int i = 1; i <= 29; i++) {
            double threshold = Math.min(baseHighSimilarityThreshold + i * 0.01, 0.99);
            ThresholdSelection candidate = buildSelection(threshold, tableScores, columnScores);
            int candidateDiff = Math.abs(targetTableCount - candidate.tableIds.size());
            int bestDiff = Math.abs(targetTableCount - best.tableIds.size());
            if (candidateDiff < bestDiff || (candidateDiff == bestDiff && candidate.threshold > best.threshold)) {
                best = candidate;
            }
        }
        return best;
    }

    private ThresholdSelection buildSelection(double threshold, Map<UUID, Double> tableScores, List<ColumnScore> columnScores) {
        Map<UUID, Double> tableScoreFromColumnRecall = new HashMap<>();
        for (ColumnScore cs : columnScores) tableScoreFromColumnRecall.merge(cs.tableId, cs.score, Math::max);
        Set<UUID> highSimilarityTableIds = tableScores.entrySet().stream()
            .filter(e -> e.getValue() >= threshold).map(Map.Entry::getKey).collect(Collectors.toSet());
        List<ColumnScore> highSimilarityColumnScores = columnScores.stream().filter(c -> c.score >= threshold).collect(Collectors.toList());
        Set<UUID> highSimilarityColumnTableIds = highSimilarityColumnScores.stream().map(c -> c.tableId).collect(Collectors.toSet());
        Map<UUID, Set<UUID>> highSimilarityColumnIdsByTable = new HashMap<>();
        for (ColumnScore cs : highSimilarityColumnScores) highSimilarityColumnIdsByTable.computeIfAbsent(cs.tableId, k -> new HashSet<>()).add(cs.columnId);

        LinkedHashSet<UUID> tableIdSet = new LinkedHashSet<>();
        tableIdSet.addAll(highSimilarityTableIds);
        tableIdSet.addAll(highSimilarityColumnTableIds);
        if (tableIdSet.isEmpty()) {
            tableScores.entrySet().stream().sorted((a,b)->Double.compare(b.getValue(), a.getValue())).limit(1).forEach(e->tableIdSet.add(e.getKey()));
            tableScoreFromColumnRecall.entrySet().stream().sorted((a,b)->Double.compare(b.getValue(), a.getValue())).limit(1).forEach(e->tableIdSet.add(e.getKey()));
        }
        List<UUID> tableIds = new ArrayList<>(tableIdSet);
        ThresholdSelection sel = new ThresholdSelection();
        sel.threshold = threshold; sel.tableIds = tableIds;
        sel.highSimilarityColumnIdsByTable = highSimilarityColumnIdsByTable;
        sel.highSimilarityTableCount = highSimilarityTableIds.size();
        sel.highSimilarityColumnTableCount = highSimilarityColumnTableIds.size();
        sel.columnTableScoreKeys = tableScoreFromColumnRecall.size();
        return sel;
    }

    private static class ColumnScore {
        UUID tableId; UUID columnId; double score;
        ColumnScore(UUID tableId, UUID columnId, double score) { this.tableId=tableId; this.columnId=columnId; this.score=score; }
    }

    private static class ThresholdSelection {
        double threshold; List<UUID> tableIds;
        Map<UUID, Set<UUID>> highSimilarityColumnIdsByTable;
        int highSimilarityTableCount; int highSimilarityColumnTableCount; int columnTableScoreKeys;
    }
}
