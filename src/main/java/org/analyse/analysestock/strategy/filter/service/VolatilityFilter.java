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
public class VolatilityFilter extends AbstractStockFilter {

    private static final BigDecimal MAX_ATR_BPS = new BigDecimal("800");

    @Override
    public String getFilterName() {
        return "VOLATILITY";
    }

    @Override
    public CandidateFilterResult check(LocalDate tradeDate, PubStockInfo stockInfo, List<StockDataDailyAll> dailyBars, List<StockMinuteData> minuteBars) {
        BigDecimal atrBps = atrBps(dailyBars, 20);
        if (!positive(atrBps)) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "无法计算ATR20", null);
        }
        if (atrBps.compareTo(MAX_ATR_BPS) > 0) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "ATR20波动过高", "atr20Bps=" + atrBps);
        }
        return FilterResultFactory.pass(tradeDate, stockCode(stockInfo), getFilterName(), "atr20Bps=" + atrBps);
    }
}
