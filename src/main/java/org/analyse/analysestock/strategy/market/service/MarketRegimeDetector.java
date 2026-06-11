package org.analyse.analysestock.strategy.market.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.mapper.StockDataDailyAllMapper;
import org.analyse.analysestock.strategy.market.entity.MarketRegime;
import org.analyse.analysestock.strategy.market.entity.MarketRegimeSnapshot;
import org.analyse.analysestock.strategy.market.mapper.MarketRegimeSnapshotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MarketRegimeDetector {

    private static final BigDecimal STRONG_BREADTH = new BigDecimal("0.50");
    private static final BigDecimal NEUTRAL_BREADTH = new BigDecimal("0.35");
    private static final BigDecimal HIGH_ATR = new BigDecimal("0.80");
    private static final BigDecimal LOW_ATR = new BigDecimal("0.50");

    @Autowired
    private StockDataDailyAllMapper stockDataDailyAllMapper;

    @Autowired
    private MarketRegimeSnapshotMapper marketRegimeSnapshotMapper;

    public MarketRegimeSnapshot detect(LocalDate tradeDate) {
        List<StockDataDailyAll> dailyBars = stockDataDailyAllMapper.selectList(new LambdaQueryWrapper<StockDataDailyAll>()
                .eq(StockDataDailyAll::getTradeDate, tradeDate));
        MarketRegimeSnapshot snapshot = buildSnapshot(tradeDate, dailyBars);
        marketRegimeSnapshotMapper.insert(snapshot);
        return snapshot;
    }

    public MarketRegimeSnapshot latest() {
        return marketRegimeSnapshotMapper.selectOne(new LambdaQueryWrapper<MarketRegimeSnapshot>()
                .orderByDesc(MarketRegimeSnapshot::getTradeDate)
                .last("LIMIT 1"));
    }

    private MarketRegimeSnapshot buildSnapshot(LocalDate tradeDate, List<StockDataDailyAll> dailyBars) {
        MarketRegimeSnapshot snapshot = new MarketRegimeSnapshot();
        snapshot.setTradeDate(tradeDate);
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshot.setUpdatedAt(LocalDateTime.now());
        if (CollectionUtils.isEmpty(dailyBars)) {
            snapshot.setRegime(MarketRegime.WEAK.name());
            snapshot.setReasoning("NO_TRADE: 当日缺少日K数据");
            return snapshot;
        }

        BigDecimal breadthRatio = calculateBreadthRatio(dailyBars);
        BigDecimal turnoverRatio = calculateTurnoverRatio(dailyBars);
        BigDecimal atr20Percentile = calculateAtr20Percentile(tradeDate, dailyBars);
        BigDecimal indexTrend = calculateIndexTrend(dailyBars);
        BigDecimal tailStrength = calculateTailStrength(dailyBars);
        MarketRegime regime = decideRegime(indexTrend, breadthRatio, turnoverRatio, atr20Percentile, tailStrength);

        snapshot.setRegime(regime.name());
        snapshot.setIndexTrend(indexTrend);
        snapshot.setBreadthRatio(breadthRatio);
        snapshot.setTurnoverRatio(turnoverRatio);
        snapshot.setAtr20Percentile(atr20Percentile);
        snapshot.setTailStrength(tailStrength);
        snapshot.setReasoning(buildReasoning(regime, indexTrend, breadthRatio, turnoverRatio, atr20Percentile, tailStrength));
        return snapshot;
    }

    private BigDecimal calculateBreadthRatio(List<StockDataDailyAll> dailyBars) {
        long validCount = dailyBars.stream().filter(this::hasCloseAndPreviousClose).count();
        if (validCount == 0) {
            return BigDecimal.ZERO;
        }
        long risingCount = dailyBars.stream()
                .filter(this::hasCloseAndPreviousClose)
                .filter(bar -> bar.getClose().compareTo(bar.getClosePrevious()) > 0)
                .count();
        return divide(BigDecimal.valueOf(risingCount), BigDecimal.valueOf(validCount));
    }

    private BigDecimal calculateTurnoverRatio(List<StockDataDailyAll> dailyBars) {
        BigDecimal totalAmount = dailyBars.stream()
                .map(StockDataDailyAll::getAmount)
                .filter(this::positive)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (!positive(totalAmount)) {
            return BigDecimal.ZERO;
        }
        BigDecimal averageAmount = divide(totalAmount, BigDecimal.valueOf(dailyBars.size()));
        return averageAmount.compareTo(new BigDecimal("100000000")) >= 0 ? BigDecimal.ONE : divide(averageAmount, new BigDecimal("100000000"));
    }

    private BigDecimal calculateAtr20Percentile(LocalDate tradeDate, List<StockDataDailyAll> dailyBars) {
        List<String> stockCodes = dailyBars.stream()
                .map(StockDataDailyAll::getStockCode)
                .filter(code -> code != null && code.length() == 6)
                .limit(100)
                .collect(Collectors.toList());
        if (stockCodes.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<StockDataDailyAll> history = stockDataDailyAllMapper.selectList(new LambdaQueryWrapper<StockDataDailyAll>()
                .in(StockDataDailyAll::getStockCode, stockCodes)
                .le(StockDataDailyAll::getTradeDate, tradeDate)
                .orderByDesc(StockDataDailyAll::getTradeDate));
        Map<String, List<StockDataDailyAll>> byStock = history.stream()
                .collect(Collectors.groupingBy(StockDataDailyAll::getStockCode));
        BigDecimal averageAtr = byStock.values().stream()
                .map(this::calculateAtrBps)
                .filter(this::positive)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = byStock.values().stream().map(this::calculateAtrBps).filter(this::positive).count();
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal atrBps = divide(averageAtr, BigDecimal.valueOf(count));
        return atrBps.compareTo(new BigDecimal("800")) >= 0 ? BigDecimal.ONE : divide(atrBps, new BigDecimal("800"));
    }

    private BigDecimal calculateIndexTrend(List<StockDataDailyAll> dailyBars) {
        BigDecimal averageChange = dailyBars.stream()
                .filter(this::hasCloseAndPreviousClose)
                .map(bar -> divide(bar.getClose().subtract(bar.getClosePrevious()), bar.getClosePrevious()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = dailyBars.stream().filter(this::hasCloseAndPreviousClose).count();
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return divide(averageChange, BigDecimal.valueOf(count));
    }

    private BigDecimal calculateTailStrength(List<StockDataDailyAll> dailyBars) {
        BigDecimal strengthSum = dailyBars.stream()
                .filter(bar -> positive(bar.getHighest()) && positive(bar.getLowest()) && positive(bar.getClose()))
                .map(bar -> divide(bar.getClose().subtract(bar.getLowest()), bar.getHighest().subtract(bar.getLowest())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = dailyBars.stream()
                .filter(bar -> positive(bar.getHighest()) && positive(bar.getLowest()) && positive(bar.getClose()) && bar.getHighest().compareTo(bar.getLowest()) > 0)
                .count();
        if (count == 0) {
            return BigDecimal.ZERO;
        }
        return divide(strengthSum, BigDecimal.valueOf(count));
    }

    private MarketRegime decideRegime(BigDecimal indexTrend, BigDecimal breadthRatio, BigDecimal turnoverRatio, BigDecimal atr20Percentile, BigDecimal tailStrength) {
        if (indexTrend.compareTo(BigDecimal.ZERO) < 0 || breadthRatio.compareTo(NEUTRAL_BREADTH) < 0 || atr20Percentile.compareTo(HIGH_ATR) > 0 || tailStrength.compareTo(new BigDecimal("0.45")) < 0) {
            return MarketRegime.WEAK;
        }
        if (indexTrend.compareTo(BigDecimal.ZERO) > 0 && breadthRatio.compareTo(STRONG_BREADTH) >= 0 && turnoverRatio.compareTo(new BigDecimal("0.60")) >= 0 && atr20Percentile.compareTo(LOW_ATR) <= 0 && tailStrength.compareTo(new BigDecimal("0.55")) >= 0) {
            return MarketRegime.STRONG;
        }
        return MarketRegime.NEUTRAL;
    }

    private String buildReasoning(MarketRegime regime, BigDecimal indexTrend, BigDecimal breadthRatio, BigDecimal turnoverRatio, BigDecimal atr20Percentile, BigDecimal tailStrength) {
        return "regime=" + regime.name()
                + ", indexTrend=" + indexTrend
                + ", breadthRatio=" + breadthRatio
                + ", turnoverRatio=" + turnoverRatio
                + ", atr20Percentile=" + atr20Percentile
                + ", tailStrength=" + tailStrength;
    }

    private BigDecimal calculateAtrBps(List<StockDataDailyAll> bars) {
        List<StockDataDailyAll> sorted = bars.stream()
                .sorted(Comparator.comparing(StockDataDailyAll::getTradeDate).reversed())
                .limit(20)
                .collect(Collectors.toList());
        if (sorted.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = sorted.stream()
                .filter(bar -> positive(bar.getHighest()) && positive(bar.getLowest()) && positive(bar.getClose()))
                .map(bar -> divide(bar.getHighest().subtract(bar.getLowest()), bar.getClose()).multiply(new BigDecimal("10000")))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return divide(total, BigDecimal.valueOf(sorted.size()));
    }

    private boolean hasCloseAndPreviousClose(StockDataDailyAll bar) {
        return positive(bar.getClose()) && positive(bar.getClosePrevious());
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private BigDecimal divide(BigDecimal left, BigDecimal right) {
        if (right == null || right.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return left.divide(right, 6, RoundingMode.HALF_UP);
    }
}
