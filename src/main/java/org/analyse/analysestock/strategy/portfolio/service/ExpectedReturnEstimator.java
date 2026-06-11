package org.analyse.analysestock.strategy.portfolio.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.mapper.StockDataDailyAllMapper;
import org.analyse.analysestock.strategy.market.entity.MarketRegime;
import org.analyse.analysestock.strategy.portfolio.entity.PortfolioDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExpectedReturnEstimator {

    private static final BigDecimal COST_BPS = new BigDecimal("15");
    private static final BigDecimal SLIPPAGE_BPS = new BigDecimal("10");

    @Autowired
    private StockDataDailyAllMapper stockDataDailyAllMapper;

    public PortfolioDecision estimate(LocalDate tradeDate, String stockCode, String regime) {
        List<StockDataDailyAll> bars = stockDataDailyAllMapper.selectList(new LambdaQueryWrapper<StockDataDailyAll>()
                        .eq(StockDataDailyAll::getStockCode, stockCode)
                        .le(StockDataDailyAll::getTradeDate, tradeDate)
                        .orderByDesc(StockDataDailyAll::getTradeDate)
                        .last("LIMIT 80"))
                .stream()
                .sorted(Comparator.comparing(StockDataDailyAll::getTradeDate).reversed())
                .collect(Collectors.toList());
        PortfolioDecision decision = new PortfolioDecision();
        decision.setTradeDate(tradeDate);
        decision.setStockCode(stockCode);
        decision.setRegime(regime);
        decision.setCostBps(COST_BPS.add(SLIPPAGE_BPS));
        decision.setCreatedAt(LocalDateTime.now());
        decision.setUpdatedAt(LocalDateTime.now());

        BigDecimal pWin = estimateWinRate(bars, regime);
        BigDecimal avgWinBps = averageMove(bars, true);
        BigDecimal avgLossBps = averageMove(bars, false).abs();
        BigDecimal riskUnit = atrBps(bars, 20);
        BigDecimal expectedReturn = pWin.multiply(avgWinBps)
                .subtract(BigDecimal.ONE.subtract(pWin).multiply(avgLossBps))
                .subtract(decision.getCostBps());
        BigDecimal rankValue = riskUnit.compareTo(BigDecimal.ZERO) > 0 ? expectedReturn.divide(riskUnit, 6, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        decision.setPWin(pWin);
        decision.setAvgWinBps(avgWinBps);
        decision.setAvgLossBps(avgLossBps);
        decision.setRiskUnit(riskUnit);
        decision.setRankValue(rankValue);
        decision.setReasoning("expectedReturnBps=" + expectedReturn + ", rankValue=" + rankValue + ", regime=" + regime);
        return decision;
    }

    private BigDecimal estimateWinRate(List<StockDataDailyAll> bars, String regime) {
        long sampleCount = bars.stream().filter(this::hasReturn).count();
        if (sampleCount == 0) {
            return MarketRegime.STRONG.name().equals(regime) ? new BigDecimal("0.52") : new BigDecimal("0.48");
        }
        long winCount = bars.stream().filter(this::hasReturn).filter(bar -> bar.getClose().compareTo(bar.getClosePrevious()) > 0).count();
        BigDecimal raw = BigDecimal.valueOf(winCount).divide(BigDecimal.valueOf(sampleCount), 6, RoundingMode.HALF_UP);
        if (MarketRegime.STRONG.name().equals(regime)) {
            return raw.add(new BigDecimal("0.03")).min(new BigDecimal("0.65"));
        }
        if (MarketRegime.WEAK.name().equals(regime)) {
            return raw.subtract(new BigDecimal("0.05")).max(new BigDecimal("0.25"));
        }
        return raw;
    }

    private BigDecimal averageMove(List<StockDataDailyAll> bars, boolean win) {
        List<BigDecimal> changes = bars.stream()
                .filter(this::hasReturn)
                .map(bar -> bar.getClose().subtract(bar.getClosePrevious()).divide(bar.getClosePrevious(), 6, RoundingMode.HALF_UP).multiply(new BigDecimal("10000")))
                .filter(change -> win ? change.compareTo(BigDecimal.ZERO) > 0 : change.compareTo(BigDecimal.ZERO) < 0)
                .collect(Collectors.toList());
        if (changes.isEmpty()) {
            return win ? new BigDecimal("150") : new BigDecimal("100");
        }
        BigDecimal total = changes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(changes.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal atrBps(List<StockDataDailyAll> bars, int days) {
        List<BigDecimal> ranges = bars.stream()
                .limit(days)
                .filter(bar -> positive(bar.getHighest()) && positive(bar.getLowest()) && positive(bar.getClose()))
                .map(bar -> bar.getHighest().subtract(bar.getLowest()).divide(bar.getClose(), 6, RoundingMode.HALF_UP).multiply(new BigDecimal("10000")))
                .collect(Collectors.toList());
        if (ranges.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return ranges.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(ranges.size()), 6, RoundingMode.HALF_UP);
    }

    private boolean hasReturn(StockDataDailyAll bar) {
        return positive(bar.getClose()) && positive(bar.getClosePrevious());
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }
}
