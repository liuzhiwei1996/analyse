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
public class ChaseFilter extends AbstractStockFilter {

    private static final BigDecimal MAX_ONE_DAY_CHANGE_BPS = new BigDecimal("600");
    private static final BigDecimal MAX_THREE_DAY_CHANGE_BPS = new BigDecimal("1200");

    @Override
    public String getFilterName() {
        return "CHASE";
    }

    @Override
    public CandidateFilterResult check(LocalDate tradeDate, PubStockInfo stockInfo, List<StockDataDailyAll> dailyBars, List<StockMinuteData> minuteBars) {
        List<StockDataDailyAll> bars = sortedDesc(dailyBars);
        if (bars.isEmpty()) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "缺少日K数据", null);
        }
        BigDecimal oneDayChange = changeBps(bars.get(0));
        BigDecimal threeDayChange = bars.stream()
                .limit(3)
                .map(this::changeBps)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (oneDayChange.compareTo(MAX_ONE_DAY_CHANGE_BPS) > 0 || threeDayChange.compareTo(MAX_THREE_DAY_CHANGE_BPS) > 0) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "短期涨幅过大", "oneDayBps=" + oneDayChange + ", threeDayBps=" + threeDayChange);
        }
        return FilterResultFactory.pass(tradeDate, stockCode(stockInfo), getFilterName(), "oneDayBps=" + oneDayChange + ", threeDayBps=" + threeDayChange);
    }
}
