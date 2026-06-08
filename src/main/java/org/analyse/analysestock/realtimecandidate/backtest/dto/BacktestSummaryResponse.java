package org.analyse.analysestock.realtimecandidate.backtest.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
/**
 * 回测汇总响应。
 *
 * <p>同时返回平铺的 TopK 结果和按成本场景分组的成本敏感性结果。</p>
 */
public class BacktestSummaryResponse {

    /**
     * 回测任务 ID。
     */
    private String taskId;

    /**
     * 回测开始日期。
     */
    private LocalDate startDate;

    /**
     * 回测结束日期。
     */
    private LocalDate endDate;

    /**
     * 有效回测交易日数。
     */
    private Integer backtestTradeDays;

    /**
     * 评分策略版本。
     */
    private String strategyVersion;

    /**
     * 按 TopK 和成本场景展开后的汇总结果。
     */
    private List<BacktestTopKResult> topKResults;

    /**
     * 成本敏感性结果，key 为成本 bps。
     */
    private Map<String, List<BacktestTopKResult>> costSensitivity;
}
