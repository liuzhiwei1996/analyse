package org.analyse.analysestock.realtimecandidate.backtest.service;

import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestSummaryResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestTaskCreateResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestTaskStatusResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.V3BacktestRequest;
import org.analyse.analysestock.analysis.entity.BacktestDailySummary;
import org.analyse.analysestock.analysis.entity.BacktestTradeDetail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * V3 回测服务接口。
 *
 * <p>在 V2 基础上增加随机选股基线、BottomK 基线、MiddleK 基线对照。</p>
 */
public interface RealtimeScoreBacktestServiceV3 {

    /**
     * 创建 V3 回测任务。
     */
    BacktestTaskCreateResponse runBacktest(V3BacktestRequest request);

    /**
     * 查询任务状态。
     */
    BacktestTaskStatusResponse getTaskStatus(String taskId);

    /**
     * 查询汇总结果（包含基线对比）。
     */
    BacktestSummaryResponse getSummary(String taskId);

    /**
     * 查询每日收益。
     */
    List<BacktestDailySummary> listDaily(String taskId, Integer topK, BigDecimal costBps,
                                         LocalDate startDate, LocalDate endDate);

    /**
     * 查询单票明细。
     */
    List<BacktestTradeDetail> listDetails(String taskId, LocalDate tradeDate, Integer topK,
                                          BigDecimal costBps, String stockCode);
}
