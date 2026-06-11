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
public class IntradayStrengthFilter extends AbstractStockFilter {

    @Override
    public String getFilterName() {
        return "INTRADAY_STRENGTH";
    }

    @Override
    public CandidateFilterResult check(LocalDate tradeDate, PubStockInfo stockInfo, List<StockDataDailyAll> dailyBars, List<StockMinuteData> minuteBars) {
        BigDecimal lastPrice = lastMinutePrice(minuteBars);
        BigDecimal vwap = vwap(minuteBars);
        BigDecimal tailVwap = tailVwap(minuteBars);
        if (!positive(lastPrice) || !positive(vwap)) {
            StockDataDailyAll latest = latestDaily(dailyBars);
            BigDecimal closeLocation = closeLocation(latest);
            if (closeLocation.compareTo(new BigDecimal("0.55")) < 0) {
                return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "尾盘强度不足", "closeLocation=" + closeLocation);
            }
            return FilterResultFactory.pass(tradeDate, stockCode(stockInfo), getFilterName(), "closeLocation=" + closeLocation);
        }
        if (lastPrice.compareTo(vwap) < 0 || (positive(tailVwap) && lastPrice.compareTo(tailVwap) < 0)) {
            return FilterResultFactory.fail(tradeDate, stockCode(stockInfo), getFilterName(), "收盘价低于VWAP或尾盘VWAP", "lastPrice=" + lastPrice + ", vwap=" + vwap + ", tailVwap=" + tailVwap);
        }
        return FilterResultFactory.pass(tradeDate, stockCode(stockInfo), getFilterName(), "lastPrice=" + lastPrice + ", vwap=" + vwap + ", tailVwap=" + tailVwap);
    }
}
