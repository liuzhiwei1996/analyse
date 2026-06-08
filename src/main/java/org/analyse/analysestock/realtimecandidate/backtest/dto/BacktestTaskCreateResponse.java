package org.analyse.analysestock.realtimecandidate.backtest.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * 创建回测任务后的轻量响应。
 */
public class BacktestTaskCreateResponse {

    /**
     * 新创建的回测任务 ID。
     */
    private String taskId;

    /**
     * 初始任务状态，通常为 PENDING。
     */
    private String status;
}
