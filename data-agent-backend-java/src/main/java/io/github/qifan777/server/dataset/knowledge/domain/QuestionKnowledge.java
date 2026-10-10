package io.github.qifan777.server.dataset.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.UUID;

@TableName("question_knowledge")
public class QuestionKnowledge {
    @TableId(type = IdType.INPUT)
    private UUID id;
    @TableField("database_id")
    private String databaseId;
    private String question;
    private String answer;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getDatabaseId() { return databaseId; }
    public void setDatabaseId(String databaseId) { this.databaseId = databaseId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
}
