package io.github.qifan777.server.trace;

import java.util.ArrayList;
import java.util.List;

/** 一次任务的汇总元数据。 */
public class TraceTask {
    private String taskId;
    private String question;
    private String role;
    private long startTime;
    private long endTime;
    private long totalMs;
    private int nodeCount;
    private String status;
    private List<TraceFrame> frames = new ArrayList<>();

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public long getTotalMs() { return totalMs; }
    public void setTotalMs(long totalMs) { this.totalMs = totalMs; }
    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public List<TraceFrame> getFrames() { return frames; }
    public void setFrames(List<TraceFrame> frames) { this.frames = frames; }
}