package io.github.qifan777.server.trace;

/** 单次节点执行帧，用于构建调用树与火焰图。 */
public class TraceFrame {
    private String id;
    private String taskId;
    private String nodeName;
    private String status;
    private long startMs;
    private long durationMs;
    private String parentId;
    private String detail;
    private String inputDetail;
    private String outputDetail;

    public TraceFrame() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getStartMs() { return startMs; }
    public void setStartMs(long startMs) { this.startMs = startMs; }
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getInputDetail() { return inputDetail; }
    public void setInputDetail(String inputDetail) { this.inputDetail = inputDetail; }
    public String getOutputDetail() { return outputDetail; }
    public void setOutputDetail(String outputDetail) { this.outputDetail = outputDetail; }
}