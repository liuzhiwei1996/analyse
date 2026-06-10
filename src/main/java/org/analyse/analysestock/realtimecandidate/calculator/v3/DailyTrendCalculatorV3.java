package org.analyse.analysestock.realtimecandidate.calculator.v3;

import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * V3 日K趋势计算器。
 *
 * <p>在现有 DailyTrendCalculator 基础上新增 avgAmplitude20d、maxDrop20d、position20d。</p>
 */
public class DailyTrendCalculatorV3 {

    public void calculate(V3FactorSnapshot snapshot, List<StockDataDailyAll> dailyBars, LocalDate tradeDate) {
        // 过滤出 T 日之前的日K，并按日期降序排列
        List<StockDataDailyAll> history = dailyBars.stream()
                .filter(d -> d.getTradeDate().isBefore(tradeDate))
                .sorted(Comparator.comparing(StockDataDailyAll::getTradeDate).reversed())
                .collect(Collectors.toList());

        if (history.isEmpty()) {
            return;
        }

        // ---- return5d = close_T_minus_1 / close_T_minus_6 - 1 ----
        if (history.size() >= 6) {
            BigDecimal closeTminus1 = history.get(0).getCloseForead();
            BigDecimal closeTminus6 = history.get(5).getCloseForead();
            if (closeTminus6 != null && closeTminus6.compareTo(BigDecimal.ZERO) > 0) {
                snapshot.setReturn5d(closeTminus1.divide(closeTminus6, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
            }
        }

        // ---- return20d = close_T_minus_1 / close_T_minus_21 - 1 ----
        if (history.size() >= 21) {
            BigDecimal closeTminus1 = history.get(0).getCloseForead();
            BigDecimal closeTminus21 = history.get(20).getCloseForead();
            if (closeTminus21 != null && closeTminus21.compareTo(BigDecimal.ZERO) > 0) {
                snapshot.setReturn20d(closeTminus1.divide(closeTminus21, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
            }
        }

        // ---- avgAmount20d ----
        int amountCount = Math.min(history.size(), 20);
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < amountCount; i++) {
            if (history.get(i).getAmount() != null) {
                totalAmount = totalAmount.add(history.get(i).getAmount());
            }
        }
        snapshot.setAvgAmount20d(totalAmount.divide(BigDecimal.valueOf(amountCount), 2, RoundingMode.HALF_UP));

        // ---- volatility20d ----
        if (history.size() >= 21) {
            List<BigDecimal> returns = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                BigDecimal c = history.get(i).getCloseForead();
                BigDecimal cp = history.get(i + 1).getCloseForead();
                if (c != null && cp != null && cp.compareTo(BigDecimal.ZERO) > 0) {
                    returns.add(c.divide(cp, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
                }
            }
            if (!returns.isEmpty()) {
                snapshot.setVolatility20d(calculateStd(returns));
            }
        }

        // ---- avgAmplitude20d（新增 V3）----
        int ampCount = Math.min(history.size(), 20);
        BigDecimal totalAmp = BigDecimal.ZERO;
        int validAmpCount = 0;
        for (int i = 0; i < ampCount; i++) {
            StockDataDailyAll bar = history.get(i);
            if (bar.getHighest() != null && bar.getLowest() != null && bar.getClosePrevious() != null
                    && bar.getClosePrevious().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal amp = bar.getHighest().subtract(bar.getLowest())
                        .divide(bar.getClosePrevious(), 6, RoundingMode.HALF_UP);
                totalAmp = totalAmp.add(amp);
                validAmpCount++;
            }
        }
        if (validAmpCount > 0) {
            snapshot.setAvgAmplitude20d(totalAmp.divide(BigDecimal.valueOf(validAmpCount), 6, RoundingMode.HALF_UP));
        }

        // ---- maxDrop20d（新增 V3）----
        int dropCount = Math.min(history.size(), 20);
        BigDecimal maxDrop = BigDecimal.ZERO;
        for (int i = 0; i < dropCount; i++) {
            BigDecimal c = history.get(i).getCloseForead();
            BigDecimal cp = history.get(i).getClosePrevious();
            if (c != null && cp != null && cp.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyReturn = c.divide(cp, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
                if (dailyReturn.compareTo(maxDrop) < 0) {
                    maxDrop = dailyReturn;
                }
            }
        }
        snapshot.setMaxDrop20d(maxDrop);

        // ---- position20d（新增 V3）：(close - low20d) / (high20d - low20d) ----
        if (history.size() >= 20) {
            BigDecimal high20d = history.get(0).getHighest();
            BigDecimal low20d = history.get(0).getLowest();
            for (int i = 1; i < 20; i++) {
                if (history.get(i).getHighest() != null && history.get(i).getHighest().compareTo(high20d) > 0) {
                    high20d = history.get(i).getHighest();
                }
                if (history.get(i).getLowest() != null && history.get(i).getLowest().compareTo(low20d) < 0) {
                    low20d = history.get(i).getLowest();
                }
            }
            if (high20d != null && low20d != null && high20d.compareTo(low20d) != 0) {
                BigDecimal close = history.get(0).getCloseForead();
                snapshot.setPosition20d(close.subtract(low20d).divide(high20d.subtract(low20d), 4, RoundingMode.HALF_UP));
            }
        }
    }

    private BigDecimal calculateStd(List<BigDecimal> values) {
        int n = values.size();
        if (n < 2) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal v : values) sum = sum.add(v);
        BigDecimal mean = sum.divide(BigDecimal.valueOf(n), 10, RoundingMode.HALF_UP);

        BigDecimal sumSq = BigDecimal.ZERO;
        for (BigDecimal v : values) {
            BigDecimal diff = v.subtract(mean);
            sumSq = sumSq.add(diff.multiply(diff));
        }
        BigDecimal var = sumSq.divide(BigDecimal.valueOf(n - 1), 10, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(Math.sqrt(var.doubleValue())).setScale(6, RoundingMode.HALF_UP);
    }
}
