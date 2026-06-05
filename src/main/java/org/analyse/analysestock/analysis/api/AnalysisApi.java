package org.analyse.analysestock.analysis.api;

import org.analyse.analysestock.analysis.vo.StockInfoVo;
import org.analyse.analysestock.config.ResultData;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/**
 * <p>
 * VIEW Mapper 接口
 * </p>
 *
 * @author kennan
 * @since 2022-03-14
 */
public interface AnalysisApi {

    @GetMapping("/getAnalysis")
    ResultData<String> getAnalysis(String stockCode);

    @PostMapping("/importStockMinuteData")
    ResultData<Integer> importStockMinuteData(StockInfoVo stockInfoVo);

    @PostMapping("/importStockDailyData")
    ResultData<Integer> importStockDailyData(StockInfoVo stockInfoVo);
}
