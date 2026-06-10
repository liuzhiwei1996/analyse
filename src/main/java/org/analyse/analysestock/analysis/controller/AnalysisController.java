package org.analyse.analysestock.analysis.controller;

import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.api.AnalysisApi;
import org.analyse.analysestock.analysis.serivce.ImportService;
import org.analyse.analysestock.analysis.vo.GenerationMissingDateRequest;
import org.analyse.analysestock.analysis.vo.GenerationMissingDateResponse;
import org.analyse.analysestock.analysis.vo.StockInfoVo;
import org.analyse.analysestock.config.ResultData;
import org.analyse.analysestock.config.ResultUtil;
import org.analyse.analysestock.realtimecandidate.dto.RealtimeCandidateScoreRecord;
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
}
