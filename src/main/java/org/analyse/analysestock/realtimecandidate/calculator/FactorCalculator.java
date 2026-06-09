package org.analyse.analysestock.realtimecandidate.calculator;

import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.analyse.analysestock.realtimecandidate.dto.FactorSnapshot;
import org.analyse.analysestock.realtimecandidate.enums.DataQualityFlag;
import org.analyse.analysestock.realtimecandidate.enums.InvalidReason;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class FactorCalculator {

    public FactorSnapshot calculate(
            String stockCode,
            LocalDate tradeDate,
            List<StockDataDailyAll> dailyBars, // 已按日期升序排列，包含 T 日之前的日K
            List<StockMinuteData> tMinuteBars // 仅 T 日的分钟线，直到 14:30
    ) {
        FactorSnapshot snapshot = new FactorSnapshot();
        snapshot.setStockCode(stockCode);
        snapshot.setTradeDate(tradeDate);

        // 1. 获取 T-1 日K 以获取昨收
        StockDataDailyAll prevDayK = dailyBars.stream()
                .filter(d -> d.getTradeDate().isBefore(tradeDate))
                .max(Comparator.comparing(StockDataDailyAll::getTradeDate))
                .orElse(null);

        if (prevDayK == null) {
            snapshot.setValid(false);
            snapshot.setInvalidReason(InvalidReason.DATA_QUALITY_ISSUE);
            return snapshot;
        }
        snapshot.setClosePrevious(prevDayK.getCloseForead());

        // 2. 提取 14:00 和 14:30 价格
        StockMinuteData min1400 = tMinuteBars.stream().filter(m -> m.getTime() != null && m.getTime() == 1400).findFirst().orElse(null);
        StockMinuteData min1430 = tMinuteBars.stream().filter(m -> m.getTime() != null && m.getTime() == 1430).findFirst().orElse(null);

        if (min1400 == null) {
            snapshot.setValid(false);
            snapshot.setInvalidReason(InvalidReason.MISSING_1400_PRICE);
            return snapshot;
        }
        if (min1430 == null) {
            snapshot.setValid(false);
            snapshot.setInvalidReason(InvalidReason.MISSING_1430_PRICE);
            return snapshot;
        }

        BigDecimal p1400 = min1400.getPrice();
        BigDecimal p1430 = min1430.getPrice();
        snapshot.setPrice1400(p1400);
        snapshot.setPrice1430(p1430);

        // 8.1 tailMomentum = price1430 / price1400 - 1
        if (p1400.compareTo(BigDecimal.ZERO) > 0) {
            snapshot.setTailMomentum(p1430.divide(p1400, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
        }

        // 8.2 intradayPosition
        BigDecimal low = tMinuteBars.stream().map(StockMinuteData::getLowPrice).min(BigDecimal::compareTo).orElse(p1430);
        BigDecimal high = tMinuteBars.stream().map(StockMinuteData::getHighPrice).max(BigDecimal::compareTo).orElse(p1430);
        snapshot.setIntradayLowBefore1430(low);
        snapshot.setIntradayHighBefore1430(high);

        if (high.compareTo(low) == 0) {
            snapshot.setIntradayPosition(new BigDecimal("0.5"));
            snapshot.setDataQualityFlag(DataQualityFlag.FLAT_INTRADAY_RANGE);
        } else {
            snapshot.setIntradayPosition(p1430.subtract(low).divide(high.subtract(low), 4, RoundingMode.HALF_UP));
        }

        // 8.3 todayTailAmount (14:00-14:30)
        BigDecimal tailAmount = tMinuteBars.stream()
                .filter(m -> m.getTime() >= 1400 && m.getTime() <= 1430)
                .map(StockMinuteData::getMinuteAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        snapshot.setTodayTailAmount(tailAmount);

        // 8.4 returnTo1430
        snapshot.setReturnTo1430(p1430.divide(prevDayK.getCloseForead(), 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));

        return snapshot;
    }
}
