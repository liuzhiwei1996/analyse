package org.analyse.analysestock.realtimecandidate.backtest.service;

import org.analyse.analysestock.analysis.entity.BacktestDailySummary;
import org.analyse.analysestock.analysis.entity.BacktestTradeDetail;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestSummaryResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestTaskCreateResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestTaskStatusResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.RealtimeScoreBacktestRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 实时候选股评分回测服务。
 */
public interface RealtimeScoreBacktestService {

    /**
     * 创建异步回测任务。
     */
    BacktestTaskCreateResponse runBacktest(RealtimeScoreBacktestRequest request);

    /**
     * 查询回测任务状态。
     */
    BacktestTaskStatusResponse getTaskStatus(String taskId);

    /**
     * 查询回测汇总指标。
     */
    BacktestSummaryResponse getSummary(String taskId);

    /**
     * 查询每日组合收益。
     */
    List<BacktestDailySummary> listDaily(String taskId, Integer topK, BigDecimal costBps, LocalDate startDate, LocalDate endDate);

    /**
     * 查询选股明细；TopK 过滤通过 rankNo <= topK 实现。
     */
    List<BacktestTradeDetail> listDetails(String taskId, LocalDate tradeDate, Integer topK, BigDecimal costBps, String stockCode);
}
