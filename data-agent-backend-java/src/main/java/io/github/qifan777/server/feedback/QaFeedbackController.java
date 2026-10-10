package io.github.qifan777.server.feedback;

import io.github.qifan777.server.agent.DataAgentSpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 问答反馈闭环：
 * 1) 点赞 -> 达到阈值后沉淀为问答证据（入库前先去重，防止知识膨胀成垃圾堆）；
 * 2) 点踩(负反馈) -> 达到阈值后撤回已沉淀的问答证据（闭环：点赞沉淀/点踩撤回）；
 * 3) 幂等 -> 每个投票人(databaseId+sessionId/operator)对同一问题只有一票，重复点击不重复计数；
 * 4) 来源 -> 记录 operator/session/source/context/answered_at，便于审计。
 */
@RestController
@RequestMapping("/qa-feedback")
public class QaFeedbackController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbc;
    private final VectorStore vectorStore;

    /** 多少位不同人肯定后自动沉淀为证据，可用 qa.feedback.promote-threshold 覆盖。 */
    @Value("${qa.feedback.promote-threshold:3}")
    private int promoteThreshold;

    /** 多少位不同人点踩后自动撤回证据，可用 qa.feedback.demote-threshold 覆盖。 */
    @Value("${qa.feedback.demote-threshold:3}")
    private int demoteThreshold;

    /** 入库前去重相似度阈值（0~1），高于则视为重复、合并不重复入库。 */
    @Value("${qa.feedback.dedup-threshold:0.85}")
    private double dedupThreshold;

    public QaFeedbackController(JdbcTemplate jdbc, VectorStore vectorStore) {
        this.jdbc = jdbc;
        this.vectorStore = vectorStore;
    }

    private String now() {
        return LocalDateTime.now().format(FMT);
    }

    private String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private void ensureSchema() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS qa_feedback ("
            + "id uuid PRIMARY KEY, "
            + "database_id varchar(255) NOT NULL, "
            + "question text NOT NULL, "
            + "answer text NOT NULL, "
            + "like_count int NOT NULL DEFAULT 0, "
            + "dislike_count int NOT NULL DEFAULT 0, "
            + "promoted boolean NOT NULL DEFAULT false, "
            + "demoted boolean NOT NULL DEFAULT false, "
            + "source varchar(64), session_id varchar(255), operator varchar(255), "
            + "context_id varchar(255), answered_at varchar(64), "
            + "promoted_at varchar(64), demoted_at varchar(64), "
            + "created_at varchar(64), updated_at varchar(64))");
        String[][] cols = {
            {"dislike_count", "int NOT NULL DEFAULT 0"},
            {"demoted", "boolean NOT NULL DEFAULT false"},
            {"source", "varchar(64)"},
            {"session_id", "varchar(255)"},
            {"operator", "varchar(255)"},
            {"context_id", "varchar(255)"},
            {"answered_at", "varchar(64)"},
            {"demoted_at", "varchar(64)"},
            {"evidence_id", "varchar(255)"},
            {"updated_at", "varchar(64)"}
        };
        for (String[] c : cols) {
            try {
                jdbc.execute("ALTER TABLE qa_feedback ADD COLUMN IF NOT EXISTS " + c[0] + " " + c[1]);
            } catch (Exception ignored) {
            }
        }
        jdbc.execute("CREATE TABLE IF NOT EXISTS qa_feedback_vote ("
            + "feedback_id uuid NOT NULL, "
            + "voter varchar(255) NOT NULL, "
            + "vote_type varchar(16) NOT NULL, "
            + "created_at varchar(64), "
            + "updated_at varchar(64), "
            + "PRIMARY KEY (feedback_id, voter))");
    }

    @PostMapping("/like")
    public Map<String, Object> like(@RequestBody Map<String, Object> body) {
        return vote(body, "like");
    }

    @PostMapping("/dislike")
    public Map<String, Object> dislike(@RequestBody Map<String, Object> body) {
        return vote(body, "dislike");
    }

    private Map<String, Object> vote(Map<String, Object> body, String voteType) {
        String question = str(body.get("question"));
        String answer = str(body.get("answer"));
        String databaseId = str(body.getOrDefault("databaseId", "ecommerce_ops"));
        if (question.isEmpty()) {
            return base("问题为空，无法记录反馈", voteType);
        }
        String voter = resolveVoter(body);
        ensureSchema();

        String sessionId = str(body.get("sessionId"));
        String operator = str(body.get("operator"));
        String source = str(body.getOrDefault("source", "chat"));
        String contextId = str(body.get("contextId"));
        String answeredAt = str(body.get("answeredAt"));
        String now = now();

        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT * FROM qa_feedback WHERE database_id = ? AND question = ?", databaseId, question);
        String rowId;
        if (existing.isEmpty()) {
            rowId = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO qa_feedback(id,database_id,question,answer,like_count,dislike_count,"
                    + "promoted,demoted,source,session_id,operator,context_id,answered_at,"
                    + "promoted_at,demoted_at,created_at,updated_at) "
                    + "VALUES(?::uuid,?,?,?,0,0,false,false,?,?,?,?,?,null,null,?,?)",
                rowId, databaseId, question, answer, source, sessionId, operator, contextId,
                answeredAt, now, now);
        } else {
            Map<String, Object> r = existing.get(0);
            rowId = String.valueOf(r.get("id"));
            String storedAnswer = r.get("answer") == null ? "" : String.valueOf(r.get("answer"));
            jdbc.update("UPDATE qa_feedback SET answer = ?, source = ?, session_id = ?, operator = ?, "
                    + "context_id = ?, answered_at = ? WHERE id = ?::uuid",
                answer.isEmpty() ? storedAnswer : answer, source, sessionId, operator, contextId,
                answeredAt.isEmpty() ? (r.get("answered_at") == null ? "" : String.valueOf(r.get("answered_at"))) : answeredAt,
                rowId);
        }

        jdbc.update("INSERT INTO qa_feedback_vote(feedback_id, voter, vote_type, created_at, updated_at) "
                + "VALUES(?::uuid,?,?,?,?) "
                + "ON CONFLICT (feedback_id, voter) "
                + "DO UPDATE SET vote_type = EXCLUDED.vote_type, updated_at = EXCLUDED.updated_at",
            rowId, voter, voteType, now, now);

        int likeCount = countVotes(rowId, "like");
        int dislikeCount = countVotes(rowId, "dislike");
        jdbc.update("UPDATE qa_feedback SET like_count = ?, dislike_count = ?, updated_at = ? WHERE id = ?::uuid",
            likeCount, dislikeCount, now, rowId);

        Map<String, Object> row = jdbc.queryForMap("SELECT promoted, demoted FROM qa_feedback WHERE id = ?::uuid", rowId);
        boolean promoted = Boolean.TRUE.equals(row.get("promoted"));
        boolean demoted = Boolean.TRUE.equals(row.get("demoted"));

        Map<String, Object> result = base("已记录反馈", voteType);
        result.put("likeCount", likeCount);
        result.put("dislikeCount", dislikeCount);
        result.put("promoted", promoted);
        result.put("demoted", demoted);
        result.put("threshold", promoteThreshold);
        result.put("demoteThreshold", demoteThreshold);

        if ("like".equals(voteType) && !promoted && likeCount >= promoteThreshold) {
            PromoteResult pr = promoteToEvidence(rowId, databaseId, question, answer, now);
            result.put("promoted", true);
            result.put("evidenceId", pr.evidenceId);
            result.put("deduped", pr.deduped);
            result.put("dupOf", pr.dupOf);
            result.put("message", pr.deduped
                ? "已被足够多人肯定，但已有高度相似证据，本次已合并而非重复入库"
                : "已被足够多人肯定，已沉淀为问答证据");
        }

        if ("dislike".equals(voteType) && promoted && !demoted && dislikeCount >= demoteThreshold) {
            demoteEvidence(rowId, databaseId, question, now);
            result.put("promoted", false);
            result.put("demoted", true);
            result.put("message", "负面反馈已达阈值，已撤回该条问答证据");
        }

        return result;
    }

    private Map<String, Object> base(String message, String voteType) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("message", message);
        m.put("voteType", voteType);
        m.put("likeCount", 0);
        m.put("dislikeCount", 0);
        m.put("promoted", false);
        m.put("demoted", false);
        m.put("threshold", promoteThreshold);
        m.put("demoteThreshold", demoteThreshold);
        return m;
    }

    private String resolveVoter(Map<String, Object> body) {
        String operator = str(body.get("operator"));
        if (!operator.isEmpty()) return operator;
        String sessionId = str(body.get("sessionId"));
        if (!sessionId.isEmpty()) return "session:" + sessionId;
        String anon = str(body.get("anonId"));
        if (!anon.isEmpty()) return "anon:" + anon;
        return "anonymous";
    }

    private int countVotes(String feedbackId, String voteType) {
        Number n = jdbc.queryForObject(
            "SELECT count(*) FROM qa_feedback_vote WHERE feedback_id = ?::uuid AND vote_type = ?",
            Integer.class, feedbackId, voteType);
        return n == null ? 0 : n.intValue();
    }

    /** 把问题+结果沉淀为证据（入库前去重），并向量化到 pgvector。 */
    private PromoteResult promoteToEvidence(String feedbackId, String databaseId, String question, String answer, String now) {
        String dupOf = null;
        double best = 0;
        List<Map<String, Object>> exists = jdbc.queryForList(
            "SELECT id, question FROM question_knowledge WHERE database_id = ?", databaseId);
        for (Map<String, Object> r : exists) {
            double s = similarity(question, r.get("question") == null ? "" : String.valueOf(r.get("question")));
            if (s > best) {
                best = s;
                dupOf = String.valueOf(r.get("id"));
            }
        }
        PromoteResult pr = new PromoteResult();
        if (dupOf != null && best >= dedupThreshold) {
            jdbc.update("UPDATE qa_feedback SET promoted = true, evidence_id = ?, promoted_at = ? WHERE id = ?::uuid",
                dupOf, now, feedbackId);
            pr.evidenceId = dupOf;
            pr.deduped = true;
            pr.dupOf = dupOf;
            return pr;
        }

        String kId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO question_knowledge(id,database_id,question,answer) VALUES(?::uuid,?,?,?)",
            kId, databaseId, question, answer.isEmpty() ? question : answer);
        List<Document> doc = new ArrayList<>();
        Document d = new Document(kId, question, Map.of(
            DataAgentSpec.Retrieval.DocumentMetadataKey.VECTOR_TYPE,
            DataAgentSpec.Retrieval.VectorType.QUESTION_KNOWLEDGE,
            DataAgentSpec.Retrieval.DocumentMetadataKey.DATABASE_ID, databaseId,
            DataAgentSpec.Retrieval.DocumentMetadataKey.KNOWLEDGE_ID, UUID.fromString(kId)));
        doc.add(d);
        vectorStore.add(doc);
        jdbc.update("UPDATE qa_feedback SET promoted = true, evidence_id = ?, promoted_at = ? WHERE id = ?::uuid",
            kId, now, feedbackId);
        pr.evidenceId = kId;
        pr.deduped = false;
        pr.dupOf = null;
        return pr;
    }

    /** 撤回证据：删除 question_knowledge 与对应向量，并把反馈标记为已撤回。 */
    private void demoteEvidence(String feedbackId, String databaseId, String question, String now) {
        // 优先按反馈上记录的 evidence_id 精确撤回（含去重合并到他人的情况）
        String evidenceId = null;
        try {
            Map<String, Object> fb = jdbc.queryForMap("SELECT evidence_id FROM qa_feedback WHERE id = ?::uuid", feedbackId);
            Object eid = fb.get("evidence_id");
            if (eid != null && !String.valueOf(eid).isBlank()) evidenceId = String.valueOf(eid);
        } catch (Exception ignored) {
        }
        List<String> ids = new ArrayList<>();
        if (evidenceId != null) {
            ids.add(evidenceId);
        } else {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id FROM question_knowledge WHERE database_id = ? AND question = ?", databaseId, question);
            for (Map<String, Object> r : rows) ids.add(String.valueOf(r.get("id")));
        }
        for (String id : ids) {
            try {
                vectorStore.delete(List.of(id));
            } catch (Exception ignored) {
            }
            try {
                jdbc.update("DELETE FROM question_knowledge WHERE id = ?::uuid", id);
            } catch (Exception ignored) {
            }
        }
        jdbc.update("UPDATE qa_feedback SET promoted = false, demoted = true, demoted_at = ? WHERE id = ?::uuid",
            now, feedbackId);
        // 一致性：共享该证据编号的其他反馈行（如去重合并进来的问题）也一并标记为撤回
        if (evidenceId != null) {
            jdbc.update("UPDATE qa_feedback SET promoted = false, demoted = true, demoted_at = ? WHERE evidence_id = ?",
                now, evidenceId);
        }
    }

    @GetMapping("/items")
    public List<Map<String, Object>> items() {
        ensureSchema();
        return jdbc.queryForList(
            "SELECT * FROM qa_feedback ORDER BY (like_count + dislike_count) DESC, created_at DESC");
    }

    private String normalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private double similarity(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) return na.equals(nb) ? 1.0 : 0.0;
        if (na.equals(nb)) return 1.0;
        Set<String> sa = new HashSet<>();
        Set<String> sb2 = new HashSet<>();
        for (int i = 0; i + 1 < na.length(); i++) sa.add(na.substring(i, i + 2));
        for (int i = 0; i + 1 < nb.length(); i++) sb2.add(nb.substring(i, i + 2));
        int inter = 0;
        for (String x : sa) if (sb2.contains(x)) inter++;
        double dice = (sa.size() + sb2.size()) == 0 ? 0.0 : (2.0 * inter) / (sa.size() + sb2.size());
        double lenSim = Math.min(na.length(), nb.length()) / (double) Math.max(na.length(), nb.length());
        return Math.max(dice * 0.8 + lenSim * 0.2, na.equals(nb) ? 1.0 : 0.0);
    }

    @DeleteMapping("/items/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        ensureSchema();
        jdbc.update("DELETE FROM qa_feedback_vote WHERE feedback_id = ?::uuid", id);
        jdbc.update("DELETE FROM qa_feedback WHERE id = ?::uuid", id);
        return Map.of("deleted", true);
    }
    private static class PromoteResult {
        String evidenceId;
        boolean deduped;
        String dupOf;
    }
}