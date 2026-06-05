package org.analyse.analysestock.realtimecandidate.calculator;

import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.analyse.analysestock.realtimecandidate.config.CostConfig;
import org.analyse.analysestock.realtimecandidate.dto.ShortSampleStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ShortSampleCalculator {

    public ShortSampleStats calculate(
            String stockCode,
            List<StockMinuteData> allMinuteBars,
            List<StockDataDailyAll> dailyBars,
            CostConfig costConfig,
            int volumeMultiplier
    ) {
        ShortSampleStats stats = new ShortSampleStats();

        // 1. 按日期分组分钟线
        Map<LocalDate, List<StockMinuteData>> minuteMap = allMinuteBars.stream()
                .filter(m -> m.getStockCode().toString().equals(stockCode))
                .collect(Collectors.groupingBy(StockMinuteData::getTradeDate));

        List<LocalDate> sortedDates = minuteMap.keySet().stream().sorted().collect(Collectors.toList());
        if (sortedDates.size() < 2) return stats;

        BigDecimal roundTripCostBps = CostCalculator.calculateRoundTripCostBps(costConfig);
        List<BigDecimal> netReturns = new ArrayList<>();

        // 2. 遍历日期，寻找 T -> T+1 样本
        for (int i = 0; i < sortedDates.size() - 1; i++) {
            LocalDate d = sortedDates.get(i);
            LocalDate nextD = sortedDates.get(i + 1);

            // D 日 14:30 价格
            List<StockMinuteData> dMinutes = minuteMap.get(d);
            BigDecimal buyPrice = dMinutes.stream()
                    .filter(m -> m.getTime() == 1430)
                    .map(StockMinuteData::getPrice)
                    .findFirst()
                    .orElse(null);

            // D+1 日 09:30-09:45 VWAP
            List<StockMinuteData> nextDMinutes = minuteMap.get(nextD);
            List<StockMinuteData> sellWindow = nextDMinutes.stream()
                    .filter(m -> m.getTime() >= 930 && m.getTime() <= 945)
                    .collect(Collectors.toList());

            BigDecimal sellVwap = VwapCalculator.calculateVwap(sellWindow, volumeMultiplier);

            if (buyPrice != null && sellVwap != null && buyPrice.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal grossReturnBps = sellVwap.subtract(buyPrice)
                        .divide(buyPrice, 8, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("10000"));
                BigDecimal netReturnBps = grossReturnBps.subtract(roundTripCostBps);
                netReturns.add(netReturnBps);
            }
        }

        if (netReturns.isEmpty()) return stats;

        // 3. 计算统计指标
        int count = netReturns.size();
        stats.setShortSampleCount(count);

        long winCount = netReturns.stream().filter(r -> r.compareTo(BigDecimal.ZERO) > 0).count();
        stats.setShortWinRate(BigDecimal.valueOf(winCount).divide(BigDecimal.valueOf(count), 4, RoundingMode.HALF_UP));

        BigDecimal totalNetReturn = netReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.setShortAvgNetReturnBps(totalNetReturn.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP));

        List<BigDecimal> winReturns = netReturns.stream().filter(r -> r.compareTo(BigDecimal.ZERO) > 0).collect(Collectors.toList());
        List<BigDecimal> lossReturns = netReturns.stream().filter(r -> r.compareTo(BigDecimal.ZERO) < 0).collect(Collectors.toList());

        if (!winReturns.isEmpty()) {
            BigDecimal avgWin = winReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(winReturns.size()), 2, RoundingMode.HALF_UP);
            stats.setShortAvgWinBps(avgWin);
        }

        if (!lossReturns.isEmpty()) {
            BigDecimal avgLoss = lossReturns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(lossReturns.size()), 2, RoundingMode.HALF_UP);
            stats.setShortAvgLossBps(avgLoss.abs());

            if (stats.getShortAvgWinBps().compareTo(BigDecimal.ZERO) > 0) {
                stats.setShortProfitLossRatio(stats.getShortAvgWinBps().divide(avgLoss.abs(), 2, RoundingMode.HALF_UP));
            }
        }

        return stats;
    }
}
