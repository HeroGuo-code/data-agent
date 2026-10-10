package io.github.qifan777.server.trace;

import io.github.qifan777.server.shared.datasource.DataFileLocator;
import io.github.qifan777.server.shared.json.JsonUtil;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 任务追踪仓库。为避免“刷新会话/后端重启后调试记录丢失”，采用磁盘持久化：
 * 每个任务一个 JSON 文件（data/trace/&lt;taskId&gt;.json），启动时自动加载，节点写入/结束时增量保存。
 */
@Component
public class TaskTraceService {

    private static final Logger log = LoggerFactory.getLogger(TaskTraceService.class);

    private final ConcurrentHashMap<String, TraceTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, TraceFrame> frames = new ConcurrentHashMap<>();
    private final AtomicInteger seq = new AtomicInteger();

    private final Path dir;

    public TaskTraceService() {
        this.dir = DataFileLocator.DATA_DIR.resolve("trace").toAbsolutePath();
    }

    @PostConstruct
    public void loadFromDisk() {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("[TRACE] 无法创建追踪目录 {}: {}", dir, e.getMessage());
            return;
        }
        int loaded = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path p : stream) {
                try {
                    String json = Files.readString(p, StandardCharsets.UTF_8);
                    TraceTask t = JsonUtil.fromJson(json, TraceTask.class);
                    if (t == null || t.getTaskId() == null) continue;
                    tasks.put(t.getTaskId(), t);
                    if (t.getFrames() != null) {
                        for (TraceFrame f : t.getFrames()) {
                            if (f != null && f.getId() != null) frames.put(f.getId(), f);
                        }
                    }
                    t.setFrames(t.getFrames() == null ? new ArrayList<>() : new ArrayList<>(t.getFrames()));
                    t.setNodeCount(t.getFrames().size());
                    loaded++;
                } catch (Exception e) {
                    log.warn("[TRACE] 加载追踪文件失败 {}: {}", p.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("[TRACE] 读取追踪目录失败 {}: {}", dir, e.getMessage());
        }
        seq.set(loaded);
        if (loaded > 0) log.info("[TRACE] 从磁盘加载 {} 条历史调试记录", loaded);
    }

    public TraceTask beginTask(String taskId, String question, String role) {
        TraceTask t = tasks.computeIfAbsent(taskId, k -> {
            TraceTask tt = new TraceTask();
            tt.setTaskId(k);
            tt.setStartTime(System.currentTimeMillis());
            tt.setStatus("运行中");
            return tt;
        });
        if (question != null) t.setQuestion(question);
        if (role != null) t.setRole(role);
        return t;
    }

    public TraceFrame start(String taskId, String nodeName, String detail) {
        TraceFrame f = new TraceFrame();
        f.setId(taskId + "-" + seq.incrementAndGet());
        f.setTaskId(taskId);
        f.setNodeName(nodeName);
        f.setStatus("运行中");
        f.setStartMs(System.currentTimeMillis());
        f.setDetail(detail);
        TraceTask t = tasks.computeIfAbsent(taskId, k -> beginTask(k, null, null));
        List<TraceFrame> fl = t.getFrames();
        if (!fl.isEmpty()) {
            f.setParentId(fl.get(fl.size() - 1).getId()); // 线性图：上一节点为父
        }
        fl.add(f);
        frames.put(f.getId(), f);
        t.setNodeCount(fl.size());
        persist(taskId);
        return f;
    }

    public void finish(TraceFrame f, String status) {
        if (f == null) return;
        f.setStatus(status);
        f.setDurationMs(System.currentTimeMillis() - f.getStartMs());
        TraceTask t = tasks.get(f.getTaskId());
        if (t != null) {
            long total = t.getFrames().stream().mapToLong(TraceFrame::getDurationMs).sum();
            t.setTotalMs(total);
        }
        if (f.getTaskId() != null) persist(f.getTaskId());
    }

    public void endTask(String taskId, String status) {
        TraceTask t = tasks.get(taskId);
        if (t == null) return;
        t.setEndTime(System.currentTimeMillis());
        t.setTotalMs(t.getEndTime() - t.getStartTime());
        t.setStatus(status);
        t.getFrames().sort(Comparator.comparingLong(TraceFrame::getStartMs));
        persist(taskId);
    }

    public List<TraceTask> listTasks() {
        List<TraceTask> out = new ArrayList<>(tasks.values());
        out.sort(Comparator.comparingLong(TraceTask::getStartTime).reversed());
        return out;
    }

    public TraceTask getTask(String taskId) {
        TraceTask t = tasks.get(taskId);
        if (t == null) return null;
        t.getFrames().sort(Comparator.comparingLong(TraceFrame::getStartMs));
        return t;
    }

    /** 内存与磁盘同步清空。 */
    public void clear() {
        tasks.clear();
        frames.clear();
        try {
            if (Files.exists(dir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
                    for (Path p : stream) Files.deleteIfExists(p);
                }
            }
        } catch (IOException e) {
            log.warn("[TRACE] 清空追踪文件失败: {}", e.getMessage());
        }
    }

    /** 将单个任务落盘，避免重启/刷新导致调试记录丢失。 */
    private void persist(String taskId) {
        TraceTask t = tasks.get(taskId);
        if (t == null) return;
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(safeName(taskId) + ".json");
            Path tmp = dir.resolve(safeName(taskId) + ".json.tmp");
            Files.writeString(tmp, JsonUtil.toJson(t), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Windows 下偶发占用时直接回退为覆盖写
                Files.writeString(target, JsonUtil.toJson(t), StandardCharsets.UTF_8);
                Files.deleteIfExists(tmp);
            }
        } catch (Exception e) {
            log.warn("[TRACE] 持久化任务失败 taskId={}: {}", taskId, e.getMessage());
        }
    }

    private String safeName(String taskId) {
        return (taskId == null ? "" : taskId).replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
