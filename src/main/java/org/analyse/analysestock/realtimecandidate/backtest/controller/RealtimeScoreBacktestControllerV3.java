package org.analyse.analysestock.realtimecandidate.backtest.controller;

import org.analyse.analysestock.analysis.entity.BacktestDailySummary;
import org.analyse.analysestock.analysis.entity.BacktestTradeDetail;
import org.analyse.analysestock.config.ResultData;
import org.analyse.analysestock.config.ResultUtil;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestSummaryResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestTaskCreateResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestTaskStatusResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.V3BacktestRequest;
import org.analyse.analysestock.realtimecandidate.backtest.service.RealtimeScoreBacktestServiceV3;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * V3 实时候选股评分回测接口。
 *
 * <p>提供任务创建、状态查询、汇总查询（含基线对比）、每日收益查询和明细查询。</p>
 */
@RestController
@RequestMapping("/api/realtime-score/backtest/v3")
public class RealtimeScoreBacktestControllerV3 {

    @Autowired
    private RealtimeScoreBacktestServiceV3 backtestServiceV3;

    /**
     * 创建 V3 异步回测任务。
     */
    @PostMapping("/run")
    public ResultData<BacktestTaskCreateResponse> run(@RequestBody V3BacktestRequest request) {
        return ResultUtil.success(backtestServiceV3.runBacktest(request));
    }

    /**
     * 查询任务状态。
     */
    @GetMapping("/task/{taskId}")
    public ResultData<BacktestTaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
        return ResultUtil.success(backtestServiceV3.getTaskStatus(taskId));
    }

    /**
     * 查询汇总结果（含基线对比）。
     */
    @GetMapping("/summary/{taskId}")
    public ResultData<BacktestSummaryResponse> getSummary(@PathVariable String taskId) {
        return ResultUtil.success(backtestServiceV3.getSummary(taskId));
    }

    /**
     * 查询每日收益。
     */
    @GetMapping("/daily/{taskId}")
    public ResultData<List<BacktestDailySummary>> listDaily(
            @PathVariable String taskId,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) BigDecimal costBps,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResultUtil.success(backtestServiceV3.listDaily(taskId, topK, costBps, startDate, endDate));
    }

    /**
     * 查询单票明细。
     */
    @GetMapping("/detail/{taskId}")
    public ResultData<List<BacktestTradeDetail>> listDetails(
            @PathVariable String taskId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) BigDecimal costBps,
            @RequestParam(required = false) String stockCode) {
        return ResultUtil.success(backtestServiceV3.listDetails(taskId, tradeDate, topK, costBps, stockCode));
    }
}
