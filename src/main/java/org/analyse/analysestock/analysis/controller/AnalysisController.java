package org.analyse.analysestock.analysis.controller;

import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.api.AnalysisApi;
import org.analyse.analysestock.analysis.serivce.ImportService;
import org.analyse.analysestock.analysis.vo.MissingStockDataItem;
import org.analyse.analysestock.analysis.vo.StockInfoVo;
import org.analyse.analysestock.config.ResultData;
import org.analyse.analysestock.config.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/anlysis")
public class AnalysisController implements AnalysisApi {

    @Autowired
    private ImportService importService;

    @Override
    public ResultData<String> getAnalysis(String stockCode) {
        return ResultUtil.success("test");
    }

    @Override
    @PostMapping("/importStockMinuteData")
    public ResultData<Integer> importStockMinuteData(@RequestBody StockInfoVo stockInfoVo) {
        return ResultUtil.success(importService.importStockMinuteData(stockInfoVo.getStockCode(), stockInfoVo.getTradeDate()));
    }

    @Override
    @PostMapping("/importStockDailyData")
    public ResultData<Integer> importStockDailyData(@RequestBody StockInfoVo stockInfoVo) {
        if (stockInfoVo.getStockCode() == null || stockInfoVo.getStockCode().equalsIgnoreCase("ALL")) {
            stockInfoVo.setStockCode(null);
        }
        if (stockInfoVo.getTradeDate() == null) {
            stockInfoVo.setTradeDate(null);
        }
        return ResultUtil.success(importService.importStockDailyData(stockInfoVo.getStockCode(), stockInfoVo.getTradeDate()));
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
