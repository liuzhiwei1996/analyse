package org.analyse.analysestock.realtimecandidate.calculator;

import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.realtimecandidate.dto.FactorSnapshot;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class DailyTrendCalculator {

    public void calculate(FactorSnapshot snapshot, List<StockDataDailyAll> dailyBars, LocalDate tradeDate) {
        // 过滤出 T 日之前的日K，并按日期降序排列
        List<StockDataDailyAll> history = dailyBars.stream()
                .filter(d -> d.getTradeDate().isBefore(tradeDate))
                .sorted(Comparator.comparing(StockDataDailyAll::getTradeDate).reversed())
                .collect(Collectors.toList());

        if (history.isEmpty()) return;

        // return5d = close_T_minus_1 / close_T_minus_6 - 1
        if (history.size() >= 6) {
            BigDecimal closeTminus1 = history.get(0).getClose();
            BigDecimal closeTminus6 = history.get(5).getClose();
            if (closeTminus6.compareTo(BigDecimal.ZERO) > 0) {
                snapshot.setReturn5d(closeTminus1.divide(closeTminus6, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
            }
        }

        // return20d = close_T_minus_1 / close_T_minus_21 - 1
        if (history.size() >= 21) {
            BigDecimal closeTminus1 = history.get(0).getClose();
            BigDecimal closeTminus21 = history.get(20).getClose();
            if (closeTminus21.compareTo(BigDecimal.ZERO) > 0) {
                snapshot.setReturn20d(closeTminus1.divide(closeTminus21, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
            }
        }

        // avgAmount20d
        int count = Math.min(history.size(), 20);
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < count; i++) {
            totalAmount = totalAmount.add(history.get(i).getAmount());
        }
        snapshot.setAvgAmount20d(totalAmount.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));

        // volatility20d = STD(日收益率，过去 20 个交易日)
        if (history.size() >= 21) {
            List<BigDecimal> returns = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                BigDecimal c = history.get(i).getClose();
                BigDecimal cp = history.get(i + 1).getClose();
                if (cp.compareTo(BigDecimal.ZERO) > 0) {
                    returns.add(c.divide(cp, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
                }
            }
            if (!returns.isEmpty()) {
                snapshot.setVolatility20d(calculateStd(returns));
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
        return sqrt(var);
    }

    private BigDecimal sqrt(BigDecimal value) {
        return BigDecimal.valueOf(Math.sqrt(value.doubleValue())).setScale(6, RoundingMode.HALF_UP);
    }
}
