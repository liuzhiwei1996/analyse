package org.analyse.analysestock.analysis.controller;

import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.api.AnalysisApi;
import org.analyse.analysestock.analysis.serivce.ImportService;
import org.analyse.analysestock.analysis.vo.GenerationMissingDateRequest;
import org.analyse.analysestock.analysis.vo.GenerationMissingDateResponse;
import org.analyse.analysestock.analysis.vo.SnapshotTaskProgress;
import org.analyse.analysestock.analysis.vo.StockInfoVo;
import org.analyse.analysestock.config.ResultData;
import org.analyse.analysestock.config.ResultUtil;
import org.analyse.analysestock.analysis.entity.RealtimeCandidateScoreResultV3;
import org.analyse.analysestock.realtimecandidate.dto.RealtimeCandidateScoreRecord;
import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;
import org.analyse.analysestock.analysis.vo.MissingStockDataItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * @Author: keenan
 * @Description:
 * @Date: create in 2026/4/23 11:11
 */
@Slf4j
@RestController
@RequestMapping("/anlysis")
public class AnalysisController implements AnalysisApi {

    @Autowired
    private ImportService importService;

    @Override
    @GetMapping("/getAnalysis")
    public ResultData<String> getAnalysis(String stockCode) {
        return ResultUtil.success("test");
    }

    @Override
    @PostMapping("/importStockMinuteData")
    public ResultData<Integer> importStockMinuteData(
            @RequestBody StockInfoVo stockInfoVo) {
        return ResultUtil.success(importService.importStockMinuteData(stockInfoVo.getStockCode(),stockInfoVo.getTradeDate()));
    }

    @Override
    @PostMapping("/importStockDailyData")
    public ResultData<Integer> importStockDailyData(@RequestBody StockInfoVo stockInfoVo) {
        if (stockInfoVo.getStockCode().equalsIgnoreCase("ALL")) {
            stockInfoVo.setStockCode(null);
        }
        if (stockInfoVo.getTradeDate() == null) {
            stockInfoVo.setTradeDate(null);
        }
        return ResultUtil.success(importService.importStockDailyData(stockInfoVo.getStockCode(), stockInfoVo.getTradeDate()));
    }

    @Override
    @PostMapping("/calculateCandidateScore")
    public ResultData<List<RealtimeCandidateScoreRecord>> calculateCandidateScore(@RequestBody StockInfoVo stockInfoVo) {
        return ResultUtil.success(importService.calculateRealtimeCandidateScores(stockInfoVo.getStockCode(), stockInfoVo.getTradeDate()));
    }

    @Override
    @PostMapping("/prepareSnapshots")
    public ResultData<String> prepareSnapshots(@RequestBody StockInfoVo stockInfoVo) {
        LocalDate tradeDate = stockInfoVo.getTradeDate();
        if (tradeDate == null) {
            tradeDate = LocalDate.now();
        }
        log.info("开始生成 {} 的全套因子快照", tradeDate);

        // 1. 生成尾盘交易快照 (基础)
        importService.prepareTailTradeSnapshot(tradeDate);

        // 2. 生成日K因子快照
        importService.prepareDailyFactorSnapshot(tradeDate);
        
        // 3. 生成短样本统计快照
        importService.prepareShortSampleStats(tradeDate);
        
        // 4. 生成市场环境和板块快照 (依赖步骤1)
        importService.prepareMarketContextSnapshot(tradeDate);

        // 5. 生成执行窗口快照 (回测新策略用)
        importService.prepareIntradayExecutionSnapshot(tradeDate);
        
        log.info("{} 的全套因子快照生成完成", tradeDate);
        return ResultUtil.success("快照生成成功");
    }

    @PostMapping("/missingGenerationDates")
    public ResultData<GenerationMissingDateResponse> findMissingGenerationDates(@RequestBody GenerationMissingDateRequest request) {
        LocalDate startDate = request == null ? null : request.getStartDate();
        LocalDate endDate = request == null ? null : request.getEndDate();
        return ResultUtil.success(importService.findMissingGenerationDates(startDate, endDate));
    }

    @Override
    @PostMapping("/checkMissingData")
    public ResultData<List<MissingStockDataItem>> checkMissingData(
            @RequestParam(required = false) String stockCode,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate) {
        return ResultUtil.success(importService.checkMissingData(stockCode, startDate, endDate));
    }

    // ==================== V3 接口 ====================

    /**
     * V3 计算实时候选股评分（REALTIME_CANDIDATE_EXECUTION_FIT_V3）。
     */
    @PostMapping("/calculateCandidateScoreV3")
    public ResultData<List<RealtimeCandidateScoreResultV3>> calculateCandidateScoreV3(@RequestBody StockInfoVo stockInfoVo) {
        return ResultUtil.success(importService.calculateRealtimeCandidateScoresV3(
                stockInfoVo.getStockCode(), stockInfoVo.getTradeDate()));
    }

    @PostMapping("/factorSnapshotsV3")
    public ResultData<List<V3FactorSnapshot>> factorSnapshotsV3(@RequestBody StockInfoVo stockInfoVo) {
        return ResultUtil.success(importService.listRealtimeCandidateFactorSnapshotsV3(
                stockInfoVo.getStockCode(), stockInfoVo.getTradeDate()));
    }

    @PostMapping("/prepareSnapshotsV3/start")
    public ResultData<SnapshotTaskProgress> startPrepareSnapshotsV3(@RequestBody StockInfoVo stockInfoVo) {
        LocalDate tradeDate = stockInfoVo == null ? null : stockInfoVo.getTradeDate();
        return ResultUtil.success(importService.startPrepareSnapshotsV3(tradeDate));
    }

    @GetMapping("/prepareSnapshotsV3/progress")
    public ResultData<SnapshotTaskProgress> prepareSnapshotsProgressV3(@RequestParam String taskId) {
        return ResultUtil.success(importService.getPrepareSnapshotsProgressV3(taskId));
    }

    /**
     * V3 准备全套快照。
     */
    @PostMapping("/prepareSnapshotsV3")
    public ResultData<String> prepareSnapshotsV3(@RequestBody StockInfoVo stockInfoVo) {
        LocalDate tradeDate = stockInfoVo.getTradeDate();
        if (tradeDate == null) {
            tradeDate = LocalDate.now();
        }
        log.info("V3: 开始生成 {} 的全套因子快照", tradeDate);

        importService.prepareTailTradeSnapshotV3(tradeDate);
        importService.prepareShortSampleStatsV3(tradeDate);
        importService.prepareMarketContextSnapshotV3(tradeDate);

        log.info("V3: {} 的全套因子快照生成完成", tradeDate);
        return ResultUtil.success("V3 快照生成成功");
    }
}
