package org.analyse.analysestock.strategy.filter.service;

import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.analyse.analysestock.strategy.filter.entity.CandidateFilterResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
public class TrendFilter extends AbstractStockFilter {

    @Override
    public String getFilterName() {
        return "TREND";
    }

    @Override
    public CandidateFilterResult check(LocalDate tradeDate, PubStockInfo stockInfo, List<StockDataDailyAll> dailyBars, List<StockMinuteData> minuteBars) {
        StockDataDailyAll latest = latestDaily(dailyBars);
        if (latest == null || !positive(latest.getClose())) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "缺少收盘价", null);
        }
        BigDecimal ma5 = movingAverage(dailyBars, 5);
        BigDecimal ma10 = movingAverage(dailyBars, 10);
        if (!positive(ma5) || !positive(ma10)) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "均线数据不足", null);
        }
        if (latest.getClose().compareTo(ma5) < 0 || latest.getClose().compareTo(ma10) < 0) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "收盘价低于MA5或MA10", "close=" + latest.getClose() + ", ma5=" + ma5 + ", ma10=" + ma10);
        }
        return FilterResultFactory.pass(tradeDate, stockCode(stockInfo), getFilterName(), "close=" + latest.getClose() + ", ma5=" + ma5 + ", ma10=" + ma10);
    }
}
