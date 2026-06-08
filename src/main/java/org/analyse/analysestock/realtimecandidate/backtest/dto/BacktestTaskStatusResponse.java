package org.analyse.analysestock.realtimecandidate.backtest.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
/**
 * 回测任务状态查询响应。
 */
public class BacktestTaskStatusResponse {

    /**
     * 回测任务 ID。
     */
    private String taskId;

    /**
     * 当前任务状态。
     */
    private String status;

    /**
     * 粗粒度进度：PENDING=0、RUNNING=50、结束=100。
     */
    private Integer progress;

    /**
     * 失败原因；任务失败时返回。
     */
    private String errorMessage;

    /**
     * 任务创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 任务开始执行时间。
     */
    private LocalDateTime startedAt;

    /**
     * 任务完成或失败时间。
     */
    private LocalDateTime finishedAt;
}
