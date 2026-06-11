package org.analyse.analysestock.strategy.filter.service;

import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.entity.StockMinuteData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractStockFilter implements StockFilter {

    protected String stockCode(PubStockInfo stockInfo) {
        return stockInfo == null ? null : stockInfo.getSymbol();
    }

    protected StockDataDailyAll latestDaily(List<StockDataDailyAll> dailyBars) {
        if (dailyBars == null || dailyBars.isEmpty()) {
            return null;
        }
        return dailyBars.stream()
                .max(Comparator.comparing(StockDataDailyAll::getTradeDate))
                .orElse(null);
    }

    protected BigDecimal movingAverage(List<StockDataDailyAll> dailyBars, int days) {
        List<StockDataDailyAll> bars = sortedDesc(dailyBars).stream()
                .limit(days)
                .filter(bar -> positive(bar.getClose()))
                .collect(Collectors.toList());
        if (bars.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = bars.stream()
                .map(StockDataDailyAll::getClose)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return divide(total, BigDecimal.valueOf(bars.size()));
    }

    protected BigDecimal atrBps(List<StockDataDailyAll> dailyBars, int days) {
        List<StockDataDailyAll> bars = sortedDesc(dailyBars).stream()
                .limit(days)
                .filter(bar -> positive(bar.getHighest()) && positive(bar.getLowest()) && positive(bar.getClose()))
                .collect(Collectors.toList());
        if (bars.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = bars.stream()
                .map(bar -> divide(bar.getHighest().subtract(bar.getLowest()), bar.getClose()).multiply(new BigDecimal("10000")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return divide(total, BigDecimal.valueOf(bars.size()));
    }

    protected BigDecimal changeBps(StockDataDailyAll bar) {
        if (bar == null || !positive(bar.getClose()) || !positive(bar.getClosePrevious())) {
            return BigDecimal.ZERO;
        }
        return divide(bar.getClose().subtract(bar.getClosePrevious()), bar.getClosePrevious()).multiply(new BigDecimal("10000"));
    }

    protected BigDecimal closeLocation(StockDataDailyAll bar) {
        if (bar == null || !positive(bar.getHighest()) || !positive(bar.getLowest()) || !positive(bar.getClose()) || bar.getHighest().compareTo(bar.getLowest()) <= 0) {
            return BigDecimal.ZERO;
        }
        return divide(bar.getClose().subtract(bar.getLowest()), bar.getHighest().subtract(bar.getLowest()));
    }

    protected BigDecimal vwap(List<StockMinuteData> minuteBars) {
        if (minuteBars == null || minuteBars.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal amount = minuteBars.stream()
                .map(StockMinuteData::getMinuteAmount)
                .filter(this::positive)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal volume = minuteBars.stream()
                .map(StockMinuteData::getMinuteVolume)
                .filter(value -> value != null && value > 0)
                .map(BigDecimal::valueOf)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return divide(amount, volume);
    }

    protected BigDecimal tailVwap(List<StockMinuteData> minuteBars) {
        if (minuteBars == null || minuteBars.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return vwap(minuteBars.stream()
                .filter(bar -> bar.getTime() != null && bar.getTime() >= 1430)
                .collect(Collectors.toList()));
    }

    protected BigDecimal lastMinutePrice(List<StockMinuteData> minuteBars) {
        if (minuteBars == null || minuteBars.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return minuteBars.stream()
                .filter(bar -> positive(bar.getPrice()))
                .max(Comparator.comparing(StockMinuteData::getTime))
                .map(StockMinuteData::getPrice)
                .orElse(BigDecimal.ZERO);
    }

    protected List<StockDataDailyAll> sortedDesc(List<StockDataDailyAll> dailyBars) {
        if (dailyBars == null) {
            return java.util.Collections.emptyList();
        }
        return dailyBars.stream()
                .filter(bar -> bar.getTradeDate() != null)
                .sorted(Comparator.comparing(StockDataDailyAll::getTradeDate).reversed())
                .collect(Collectors.toList());
    }

    protected boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    protected BigDecimal divide(BigDecimal left, BigDecimal right) {
        if (left == null || right == null || right.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return left.divide(right, 6, RoundingMode.HALF_UP);
    }
}
