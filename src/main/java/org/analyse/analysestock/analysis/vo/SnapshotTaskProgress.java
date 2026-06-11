package org.analyse.analysestock.analysis.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class SnapshotTaskProgress {

    private String taskId;

    private LocalDate tradeDate;

    private volatile String status;

    private volatile String stage;

    private volatile int processed;

    private volatile int total;

    private volatile int generated;

    private volatile String message;

    private volatile String errorMessage;

    private LocalDateTime startedAt;

    private volatile LocalDateTime updatedAt;

    private volatile LocalDateTime finishedAt;

    public static SnapshotTaskProgress create(String taskId, LocalDate tradeDate) {
        SnapshotTaskProgress progress = new SnapshotTaskProgress();
        progress.setTaskId(taskId);
        progress.setTradeDate(tradeDate);
        progress.setStatus("RUNNING");
        progress.setStage("QUEUED");
        progress.setMessage("任务已提交，等待执行");
        progress.setStartedAt(LocalDateTime.now());
        progress.setUpdatedAt(progress.getStartedAt());
        return progress;
    }

    public static SnapshotTaskProgress notFound(String taskId) {
        SnapshotTaskProgress progress = new SnapshotTaskProgress();
        progress.setTaskId(taskId);
        progress.setStatus("NOT_FOUND");
        progress.setStage("UNKNOWN");
        progress.setMessage("未找到任务，可能服务已重启或 taskId 不正确");
        progress.setUpdatedAt(LocalDateTime.now());
        return progress;
    }

    public synchronized void startStage(String stage, int total, String message) {
        this.status = "RUNNING";
        this.stage = stage;
        this.processed = 0;
        this.total = Math.max(total, 0);
        this.generated = 0;
        this.message = message;
        this.updatedAt = LocalDateTime.now();
    }

    public synchronized void update(int processed, int generated, String message) {
        this.processed = Math.max(this.processed, processed);
        this.generated = Math.max(this.generated, generated);
        if (message != null) {
            this.message = message;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public synchronized void complete(String message) {
        this.status = "SUCCESS";
        this.stage = "DONE";
        this.processed = this.total;
        this.message = message;
        this.updatedAt = LocalDateTime.now();
        this.finishedAt = this.updatedAt;
    }

    public synchronized void fail(Throwable throwable) {
        this.status = "FAILED";
        this.message = "任务执行失败";
        this.errorMessage = throwable == null ? null : throwable.getMessage();
        this.updatedAt = LocalDateTime.now();
        this.finishedAt = this.updatedAt;
    }
}
