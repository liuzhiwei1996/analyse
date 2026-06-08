package org.analyse.analysestock.realtimecandidate.backtest.controller;

import org.analyse.analysestock.analysis.entity.BacktestDailySummary;
import org.analyse.analysestock.analysis.entity.BacktestTradeDetail;
import org.analyse.analysestock.config.ResultData;
import org.analyse.analysestock.config.ResultUtil;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestSummaryResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestTaskCreateResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestTaskStatusResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.RealtimeScoreBacktestRequest;
import org.analyse.analysestock.realtimecandidate.backtest.service.RealtimeScoreBacktestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 实时候选股评分回测接口。
 *
 * <p>提供任务创建、状态查询、汇总查询、每日收益查询和明细查询。</p>
 */
@RestController
@RequestMapping("/api/realtime-score/backtest")
public class RealtimeScoreBacktestController {

    @Autowired
    private RealtimeScoreBacktestService realtimeScoreBacktestService;

    /**
     * 创建异步回测任务。
     */
    @PostMapping("/run")
    public ResultData<BacktestTaskCreateResponse> run(@RequestBody RealtimeScoreBacktestRequest request) {
        return ResultUtil.success(realtimeScoreBacktestService.runBacktest(request));
    }

    /**
     * 查询任务状态和粗粒度进度。
     */
    @GetMapping("/task/{taskId}")
    public ResultData<BacktestTaskStatusResponse> getTask(@PathVariable String taskId) {
        return ResultUtil.success(realtimeScoreBacktestService.getTaskStatus(taskId));
    }

    /**
     * 查询 TopK 汇总和成本敏感性结果。
     */
    @GetMapping("/summary/{taskId}")
    public ResultData<BacktestSummaryResponse> getSummary(@PathVariable String taskId) {
        return ResultUtil.success(realtimeScoreBacktestService.getSummary(taskId));
    }

    /**
     * 查询每日组合收益，支持按 TopK、成本和日期过滤。
     */
    @GetMapping("/daily/{taskId}")
    public ResultData<List<BacktestDailySummary>> listDaily(
            @PathVariable String taskId,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) BigDecimal costBps,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResultUtil.success(realtimeScoreBacktestService.listDaily(taskId, topK, costBps, startDate, endDate));
    }

    /**
     * 查询选股明细，TopK 参数会转换为 rankNo <= topK。
     */
    @GetMapping("/detail/{taskId}")
    public ResultData<List<BacktestTradeDetail>> listDetails(
            @PathVariable String taskId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) BigDecimal costBps,
            @RequestParam(required = false) String stockCode
    ) {
        return ResultUtil.success(realtimeScoreBacktestService.listDetails(taskId, tradeDate, topK, costBps, stockCode));
    }
}
