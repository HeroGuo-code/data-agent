package io.github.qifan777.server.trace;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 调试管理：任务列表 + 调用树 / 火焰图数据。 */
@RestController
@RequestMapping("/trace")
public class TraceController {

    private final TaskTraceService traceService;
    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public TraceController(TaskTraceService traceService) { this.traceService = traceService; }

    @GetMapping("/tasks")
    public List<TraceTaskView> tasks() {
        return traceService.listTasks().stream().map(this::toView).toList();
    }

    @GetMapping("/tasks/{taskId}")
    public TraceTask task(@PathVariable String taskId) {
        return traceService.getTask(taskId);
    }

    @GetMapping("/tasks/{taskId}/flame")
    public TraceTask flame(@PathVariable String taskId) {
        return traceService.getTask(taskId);
    }

    private TraceTaskView toView(TraceTask t) {
        String start = t.getStartTime() == 0 ? "" : FMT.format(Instant.ofEpochMilli(t.getStartTime()));
        String end = t.getEndTime() == 0 ? "" : FMT.format(Instant.ofEpochMilli(t.getEndTime()));
        return new TraceTaskView(t.getTaskId(), t.getQuestion(), t.getRole(), start, end,
            t.getTotalMs(), t.getNodeCount(), t.getStatus());
    }

    public record TraceTaskView(String taskId, String question, String role, String startTime,
                                String endTime, long totalMs, int nodeCount, String status) {}
}