package org.analyse.analysestock.analysis.controller;

import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.api.AnalysisApi;
import org.analyse.analysestock.analysis.serivce.ImportService;
import org.analyse.analysestock.analysis.vo.StockInfoVo;
import org.analyse.analysestock.config.ResultData;
import org.analyse.analysestock.config.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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
}
