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
public class LiquidityFilter extends AbstractStockFilter {

    private static final BigDecimal MIN_AVG_AMOUNT = new BigDecimal("50000000");

    @Override
    public String getFilterName() {
        return "LIQUIDITY";
    }

    @Override
    public CandidateFilterResult check(LocalDate tradeDate, PubStockInfo stockInfo, List<StockDataDailyAll> dailyBars, List<StockMinuteData> minuteBars) {
        List<StockDataDailyAll> bars = sortedDesc(dailyBars);
        if (bars.isEmpty()) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "缺少日K数据", null);
        }
        BigDecimal total = bars.stream()
                .limit(20)
                .map(StockDataDailyAll::getAmount)
                .filter(this::positive)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgAmount = divide(total, BigDecimal.valueOf(Math.min(20, bars.size())));
        if (avgAmount.compareTo(MIN_AVG_AMOUNT) < 0) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "20日均成交额不足", "avgAmount=" + avgAmount);
        }
        return FilterResultFactory.pass(tradeDate, stockCode(stockInfo), getFilterName(), "avgAmount=" + avgAmount);
    }
}
