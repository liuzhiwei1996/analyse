package org.analyse.analysestock.realtimecandidate.calculator.v3;

import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.analyse.analysestock.analysis.entity.StockTailTradeSnapshotV3;
import org.analyse.analysestock.realtimecandidate.calculator.CostCalculator;
import org.analyse.analysestock.realtimecandidate.config.CostConfig;
import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * V3 短样本统计计算器。
 *
 * <p>基于<strong>分阶段买卖规则</strong>（非简单 VWAP）统计每只股票的历史表现。</p>
 *
 * <p>买卖规则（与需求文档一致）：</p>
 * <ul>
 *   <li>T 日 14:30 生成候选股，以 14:30 价格为基准价</li>
 *   <li>T 日 14:35 后跌 3% 买入；否则 14:45 后跌 2% 买入；否则 14:55 后跌 1% 买入</li>
 *   <li>T+1 日 09:35 前涨 3% 卖出；否则 09:40 前涨 2% 卖出；否则 09:44 前涨 1% 卖出；否则 09:45 强制卖出</li>
 * </ul>
 *
 * <p>贝叶斯修正：adjustedWinRate = (n * observedWinRate + priorStrength * priorWinRate) / (n + priorStrength)</p>
 */
public class ShortSampleCalculatorV3 {

    /** 贝叶斯先验强度（可配置） */
    private static final int PRIOR_STRENGTH = 10;
    /** 贝叶斯先验胜率 */
    private static final BigDecimal PRIOR_WIN_RATE = new BigDecimal("0.5");

    /** 买入触发参数 */
    private static final BigDecimal BUY_DROP_3PCT = new BigDecimal("0.97");
    private static final BigDecimal BUY_DROP_2PCT = new BigDecimal("0.98");
    private static final BigDecimal BUY_DROP_1PCT = new BigDecimal("0.99");

    /** 卖出触发参数 */
    private static final BigDecimal SELL_PROFIT_3PCT = new BigDecimal("1.03");
    private static final BigDecimal SELL_PROFIT_2PCT = new BigDecimal("1.02");
    private static final BigDecimal SELL_PROFIT_1PCT = new BigDecimal("1.01");

    private static final BigDecimal BPS_BASE = new BigDecimal("10000");

    /**
     * 计算某只股票在指定交易日之前的短样本统计。
     *
     * @param stockCode    股票代码
     * @param allMinuteBars 所有分钟线数据（包含多日）
     * @param costConfig   成本配置
     * @param tradeDate    当前交易日（只使用此日期之前的数据）
     * @return 填充了短样本统计的 V3FactorSnapshot（修改传入的 snapshot 或创建新的）
     */
    public void calculate(V3FactorSnapshot snapshot, String stockCode,
                          List<StockMinuteData> allMinuteBars, CostConfig costConfig,
                          LocalDate tradeDate) {

        // 1. 按日期分组分钟线
        Map<LocalDate, List<StockMinuteData>> minuteMap = allMinuteBars.stream()
                .filter(m -> {
                    if (m.getStockCode() == null) return false;
                    String mCodeStr = m.getStockCode().toString();
                    if (mCodeStr.length() < 6) mCodeStr = String.format("%06d", m.getStockCode());
                    String pureStockCode = stockCode.length() > 6 ? stockCode.substring(stockCode.length() - 6) : stockCode;
                    if (pureStockCode.length() < 6) pureStockCode = String.format("%06d", Integer.parseInt(stockCode));
                    return mCodeStr.equals(pureStockCode);
                })
                .filter(m -> m.getTradeDate() != null && m.getTradeDate().isBefore(tradeDate))
                .collect(Collectors.groupingBy(StockMinuteData::getTradeDate));

        List<LocalDate> sortedDates = minuteMap.keySet().stream().sorted().collect(Collectors.toList());
        if (sortedDates.size() < 2) {
            snapshot.setShortSampleCount(0);
            return;
        }

        BigDecimal roundTripCostBps = CostCalculator.calculateRoundTripCostBps(costConfig);
        List<TradeSample> samples = new ArrayList<>();

        // 2. 遍历日期，模拟分阶段买卖
        for (int i = 0; i < sortedDates.size() - 1; i++) {
            LocalDate d = sortedDates.get(i);
            LocalDate nextD = sortedDates.get(i + 1);

            List<StockMinuteData> dMinutes = minuteMap.get(d);
            List<StockMinuteData> nextDMinutes = minuteMap.get(nextD);

            // T 日 14:30 基准价
            BigDecimal basePrice = dMinutes.stream()
                    .filter(m -> m.getTime() != null && m.getTime() == 1430)
                    .map(StockMinuteData::getPrice)
                    .findFirst().orElse(null);
            if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) continue;

            // T 日尾盘各窗口最低价
            BigDecimal low14351444 = getMinPriceInWindow(dMinutes, 1435, 1444);
            BigDecimal low14451454 = getMinPriceInWindow(dMinutes, 1445, 1454);
            BigDecimal low14551500 = getMinPriceInWindow(dMinutes, 1455, 1500);

            // T+1 日早盘各窗口最高价
            BigDecimal high09300935 = getMaxPriceInWindow(nextDMinutes, 930, 935);
            BigDecimal high09360940 = getMaxPriceInWindow(nextDMinutes, 936, 940);
            BigDecimal high09410944 = getMaxPriceInWindow(nextDMinutes, 941, 944);
            BigDecimal price0945 = getPriceAt(nextDMinutes, 945);

            // 模拟买入
            TradeSample sample = new TradeSample();
            BigDecimal trigger3 = basePrice.multiply(BUY_DROP_3PCT);
            BigDecimal trigger2 = basePrice.multiply(BUY_DROP_2PCT);
            BigDecimal trigger1 = basePrice.multiply(BUY_DROP_1PCT);

            if (low14351444 != null && low14351444.compareTo(trigger3) <= 0) {
                sample.buyRule = "BUY_DROP_3PCT_AFTER_1435";
                sample.buyPrice = trigger3;
                sample.buyFilled = true;
            } else if (low14451454 != null && low14451454.compareTo(trigger2) <= 0) {
                sample.buyRule = "BUY_DROP_2PCT_AFTER_1445";
                sample.buyPrice = trigger2;
                sample.buyFilled = true;
            } else if (low14551500 != null && low14551500.compareTo(trigger1) <= 0) {
                sample.buyRule = "BUY_DROP_1PCT_AFTER_1455";
                sample.buyPrice = trigger1;
                sample.buyFilled = true;
            } else {
                sample.buyRule = "NOT_FILLED";
                sample.buyFilled = false;
            }

            if (!sample.buyFilled) {
                samples.add(sample);
                continue;
            }

            // 模拟卖出
            BigDecimal sellTrigger3 = sample.buyPrice.multiply(SELL_PROFIT_3PCT);
            BigDecimal sellTrigger2 = sample.buyPrice.multiply(SELL_PROFIT_2PCT);
            BigDecimal sellTrigger1 = sample.buyPrice.multiply(SELL_PROFIT_1PCT);

            if (high09300935 != null && high09300935.compareTo(sellTrigger3) >= 0) {
                sample.sellRule = "SELL_PROFIT_3PCT_BEFORE_0935";
                sample.sellPrice = sellTrigger3;
            } else if (high09360940 != null && high09360940.compareTo(sellTrigger2) >= 0) {
                sample.sellRule = "SELL_PROFIT_2PCT_BEFORE_0940";
                sample.sellPrice = sellTrigger2;
            } else if (high09410944 != null && high09410944.compareTo(sellTrigger1) >= 0) {
                sample.sellRule = "SELL_PROFIT_1PCT_BEFORE_0944";
                sample.sellPrice = sellTrigger1;
            } else if (price0945 != null) {
                sample.sellRule = "FORCE_SELL_0945";
                sample.sellPrice = price0945;
            } else {
                // 无法确定卖出价，跳过
                continue;
            }

            // 计算收益
            sample.grossReturnBps = sample.sellPrice.subtract(sample.buyPrice)
                    .divide(sample.buyPrice, 8, RoundingMode.HALF_UP)
                    .multiply(BPS_BASE).setScale(4, RoundingMode.HALF_UP);
            sample.netReturnBps = sample.grossReturnBps.subtract(roundTripCostBps)
                    .setScale(4, RoundingMode.HALF_UP);
            sample.winFlag = sample.netReturnBps.compareTo(BigDecimal.ZERO) > 0;

            samples.add(sample);
        }

        if (samples.isEmpty()) {
            snapshot.setShortSampleCount(0);
            return;
        }

        // 3. 统计指标
        int totalCount = samples.size();
        snapshot.setShortSampleCount(totalCount);

        List<TradeSample> filledSamples = samples.stream().filter(s -> s.buyFilled).collect(Collectors.toList());
        int filledCount = filledSamples.size();

        // 买入成交统计
        if (totalCount > 0) {
            long buy3Count = samples.stream().filter(s -> "BUY_DROP_3PCT_AFTER_1435".equals(s.buyRule)).count();
            long buy2Count = samples.stream().filter(s -> "BUY_DROP_2PCT_AFTER_1445".equals(s.buyRule)).count();
            long buy1Count = samples.stream().filter(s -> "BUY_DROP_1PCT_AFTER_1455".equals(s.buyRule)).count();
            long notFilledCount = samples.stream().filter(s -> "NOT_FILLED".equals(s.buyRule)).count();

            snapshot.setBuyFillRate(rate(filledCount, totalCount));
            snapshot.setBuy3PctFillRate(rate(buy3Count, totalCount));
            snapshot.setBuy2PctFillRate(rate(buy2Count, totalCount));
            snapshot.setBuy1PctFillRate(rate(buy1Count, totalCount));
            snapshot.setNotFilledRate(rate(notFilledCount, totalCount));
        }

        // 止盈/强制卖出统计
        if (filledCount > 0) {
            long tp1Count = filledSamples.stream().filter(s -> "SELL_PROFIT_1PCT_BEFORE_0944".equals(s.sellRule)).count();
            long tp2Count = filledSamples.stream().filter(s -> "SELL_PROFIT_2PCT_BEFORE_0940".equals(s.sellRule)).count();
            long tp3Count = filledSamples.stream().filter(s -> "SELL_PROFIT_3PCT_BEFORE_0935".equals(s.sellRule)).count();
            long fsCount = filledSamples.stream().filter(s -> "FORCE_SELL_0945".equals(s.sellRule)).count();

            snapshot.setTakeProfit1PctRate(rate(tp1Count, filledCount));
            snapshot.setTakeProfit2PctRate(rate(tp2Count, filledCount));
            snapshot.setTakeProfit3PctRate(rate(tp3Count, filledCount));
            snapshot.setForceSellRate(rate(fsCount, filledCount));
        }

        // 收益统计
        if (filledCount > 0) {
            List<BigDecimal> netReturns = filledSamples.stream().map(s -> s.netReturnBps).collect(Collectors.toList());
            List<BigDecimal> grossReturns = filledSamples.stream().map(s -> s.grossReturnBps).collect(Collectors.toList());

            BigDecimal avgNetReturnBps = avg(netReturns);
            snapshot.setAvgNetReturnBps(avgNetReturnBps);
            snapshot.setShortAvgNetReturnBps(avgNetReturnBps);
            snapshot.setAvgGrossReturnBps(avg(grossReturns));
            snapshot.setMaxLossBps(netReturns.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));

            // 止盈平均收益
            List<BigDecimal> tpReturns = filledSamples.stream()
                    .filter(s -> !"FORCE_SELL_0945".equals(s.sellRule))
                    .map(s -> s.netReturnBps).collect(Collectors.toList());
            snapshot.setAvgTakeProfitReturnBps(tpReturns.isEmpty() ? BigDecimal.ZERO : avg(tpReturns));

            // 强制卖出平均收益
            List<BigDecimal> fsReturns = filledSamples.stream()
                    .filter(s -> "FORCE_SELL_0945".equals(s.sellRule))
                    .map(s -> s.netReturnBps).collect(Collectors.toList());
            snapshot.setAvgForceSellReturnBps(fsReturns.isEmpty() ? BigDecimal.ZERO : avg(fsReturns));

            // 强制卖出亏损比例
            long fsLossCount = filledSamples.stream()
                    .filter(s -> "FORCE_SELL_0945".equals(s.sellRule) && !s.winFlag).count();
            long fsAllCount = filledSamples.stream().filter(s -> "FORCE_SELL_0945".equals(s.sellRule)).count();
            snapshot.setForceSellLossRate(fsAllCount > 0 ? rate(fsLossCount, fsAllCount) : BigDecimal.ZERO);

            // 分档收益
            List<TradeSample> buy3Samples = filledSamples.stream()
                    .filter(s -> "BUY_DROP_3PCT_AFTER_1435".equals(s.buyRule)).collect(Collectors.toList());
            List<TradeSample> buy2Samples = filledSamples.stream()
                    .filter(s -> "BUY_DROP_2PCT_AFTER_1445".equals(s.buyRule)).collect(Collectors.toList());
            List<TradeSample> buy1Samples = filledSamples.stream()
                    .filter(s -> "BUY_DROP_1PCT_AFTER_1455".equals(s.buyRule)).collect(Collectors.toList());

            snapshot.setBuy3PctAvgNetReturnBps(buy3Samples.isEmpty() ? null : avg(buy3Samples.stream().map(s -> s.netReturnBps).collect(Collectors.toList())));
            snapshot.setBuy2PctAvgNetReturnBps(buy2Samples.isEmpty() ? null : avg(buy2Samples.stream().map(s -> s.netReturnBps).collect(Collectors.toList())));
            snapshot.setBuy1PctAvgNetReturnBps(buy1Samples.isEmpty() ? null : avg(buy1Samples.stream().map(s -> s.netReturnBps).collect(Collectors.toList())));

            // 胜率
            long winCount = filledSamples.stream().filter(s -> s.winFlag).count();
            snapshot.setShortRawWinRate(rate(winCount, filledCount));

            // 盈亏比
            List<BigDecimal> winReturns = filledSamples.stream().filter(s -> s.winFlag).map(s -> s.netReturnBps).collect(Collectors.toList());
            List<BigDecimal> lossReturns = filledSamples.stream().filter(s -> !s.winFlag).map(s -> s.netReturnBps).collect(Collectors.toList());
            snapshot.setShortAvgWinBps(winReturns.isEmpty() ? BigDecimal.ZERO : avg(winReturns));
            snapshot.setShortAvgLossBps(lossReturns.isEmpty() ? BigDecimal.ZERO : avg(lossReturns).abs());
            if (!lossReturns.isEmpty() && snapshot.getShortAvgWinBps().compareTo(BigDecimal.ZERO) > 0) {
                snapshot.setShortProfitLossRatio(snapshot.getShortAvgWinBps().divide(snapshot.getShortAvgLossBps(), 2, RoundingMode.HALF_UP));
            }
        }

        // 4. 贝叶斯修正胜率
        if (filledCount > 0) {
            BigDecimal rawWinRate = snapshot.getShortRawWinRate();
            BigDecimal adjustedWinRate = rawWinRate.multiply(BigDecimal.valueOf(filledCount))
                    .add(PRIOR_WIN_RATE.multiply(BigDecimal.valueOf(PRIOR_STRENGTH)))
                    .divide(BigDecimal.valueOf(filledCount + PRIOR_STRENGTH), 4, RoundingMode.HALF_UP);
            snapshot.setShortAdjustedWinRate(adjustedWinRate);
        } else {
            snapshot.setShortAdjustedWinRate(PRIOR_WIN_RATE);
        }
    }

    // ==================== 辅助方法 ====================

    public void calculateForStockMinutes(V3FactorSnapshot snapshot,
                                         List<StockMinuteData> stockMinuteBars,
                                         CostConfig costConfig,
                                         LocalDate tradeDate) {
        Map<LocalDate, List<StockMinuteData>> minuteMap = stockMinuteBars.stream()
                .filter(m -> m.getTradeDate() != null && m.getTradeDate().isBefore(tradeDate))
                .collect(Collectors.groupingBy(StockMinuteData::getTradeDate));
        calculateFromMinuteMap(snapshot, minuteMap, costConfig);
    }

    public void calculateFromSnapshots(V3FactorSnapshot snapshot,
                                       List<StockTailTradeSnapshotV3> stockSnapshots,
                                       CostConfig costConfig,
                                       LocalDate tradeDate) {
        List<StockTailTradeSnapshotV3> sorted = stockSnapshots.stream()
                .filter(s -> s.getTradeDate() != null && s.getTradeDate().isBefore(tradeDate))
                .sorted(Comparator.comparing(StockTailTradeSnapshotV3::getTradeDate))
                .collect(Collectors.toList());
        if (sorted.size() < 2) {
            snapshot.setShortSampleCount(0);
            return;
        }

        BigDecimal roundTripCostBps = CostCalculator.calculateRoundTripCostBps(costConfig);
        List<TradeSample> samples = new ArrayList<>();

        for (int i = 0; i < sorted.size() - 1; i++) {
            StockTailTradeSnapshotV3 buySnapshot = sorted.get(i);
            StockTailTradeSnapshotV3 sellSnapshot = sorted.get(i + 1);
            BigDecimal basePrice = buySnapshot.getPrice1430();
            if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) continue;

            TradeSample sample = new TradeSample();
            BigDecimal trigger3 = basePrice.multiply(BUY_DROP_3PCT);
            BigDecimal trigger2 = basePrice.multiply(BUY_DROP_2PCT);
            BigDecimal trigger1 = basePrice.multiply(BUY_DROP_1PCT);

            if (buySnapshot.getLow14351444() != null && buySnapshot.getLow14351444().compareTo(trigger3) <= 0) {
                sample.buyRule = "BUY_DROP_3PCT_AFTER_1435";
                sample.buyPrice = trigger3;
                sample.buyFilled = true;
            } else if (buySnapshot.getLow14451454() != null && buySnapshot.getLow14451454().compareTo(trigger2) <= 0) {
                sample.buyRule = "BUY_DROP_2PCT_AFTER_1445";
                sample.buyPrice = trigger2;
                sample.buyFilled = true;
            } else if (buySnapshot.getLow14551500() != null && buySnapshot.getLow14551500().compareTo(trigger1) <= 0) {
                sample.buyRule = "BUY_DROP_1PCT_AFTER_1455";
                sample.buyPrice = trigger1;
                sample.buyFilled = true;
            } else {
                sample.buyRule = "NOT_FILLED";
                sample.buyFilled = false;
            }

            if (!sample.buyFilled) {
                samples.add(sample);
                continue;
            }

            BigDecimal sellTrigger3 = sample.buyPrice.multiply(SELL_PROFIT_3PCT);
            BigDecimal sellTrigger2 = sample.buyPrice.multiply(SELL_PROFIT_2PCT);
            BigDecimal sellTrigger1 = sample.buyPrice.multiply(SELL_PROFIT_1PCT);

            if (sellSnapshot.getHigh09300935() != null && sellSnapshot.getHigh09300935().compareTo(sellTrigger3) >= 0) {
                sample.sellRule = "SELL_PROFIT_3PCT_BEFORE_0935";
                sample.sellPrice = sellTrigger3;
            } else if (sellSnapshot.getHigh09360940() != null && sellSnapshot.getHigh09360940().compareTo(sellTrigger2) >= 0) {
                sample.sellRule = "SELL_PROFIT_2PCT_BEFORE_0940";
                sample.sellPrice = sellTrigger2;
            } else if (sellSnapshot.getHigh09410944() != null && sellSnapshot.getHigh09410944().compareTo(sellTrigger1) >= 0) {
                sample.sellRule = "SELL_PROFIT_1PCT_BEFORE_0944";
                sample.sellPrice = sellTrigger1;
            } else if (sellSnapshot.getPrice0945() != null) {
                sample.sellRule = "FORCE_SELL_0945";
                sample.sellPrice = sellSnapshot.getPrice0945();
            } else {
                continue;
            }

            sample.grossReturnBps = sample.sellPrice.subtract(sample.buyPrice)
                    .divide(sample.buyPrice, 8, RoundingMode.HALF_UP)
                    .multiply(BPS_BASE).setScale(4, RoundingMode.HALF_UP);
            sample.netReturnBps = sample.grossReturnBps.subtract(roundTripCostBps)
                    .setScale(4, RoundingMode.HALF_UP);
            sample.winFlag = sample.netReturnBps.compareTo(BigDecimal.ZERO) > 0;
            samples.add(sample);
        }

        populateStats(snapshot, samples);
    }

    private void calculateFromMinuteMap(V3FactorSnapshot snapshot,
                                        Map<LocalDate, List<StockMinuteData>> minuteMap,
                                        CostConfig costConfig) {
        List<LocalDate> sortedDates = minuteMap.keySet().stream().sorted().collect(Collectors.toList());
        if (sortedDates.size() < 2) {
            snapshot.setShortSampleCount(0);
            return;
        }

        BigDecimal roundTripCostBps = CostCalculator.calculateRoundTripCostBps(costConfig);
        List<TradeSample> samples = new ArrayList<>();

        for (int i = 0; i < sortedDates.size() - 1; i++) {
            LocalDate d = sortedDates.get(i);
            LocalDate nextD = sortedDates.get(i + 1);

            List<StockMinuteData> dMinutes = minuteMap.get(d);
            List<StockMinuteData> nextDMinutes = minuteMap.get(nextD);

            BigDecimal basePrice = dMinutes.stream()
                    .filter(m -> m.getTime() != null && m.getTime() == 1430)
                    .map(StockMinuteData::getPrice)
                    .findFirst().orElse(null);
            if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal low14351444 = getMinPriceInWindow(dMinutes, 1435, 1444);
            BigDecimal low14451454 = getMinPriceInWindow(dMinutes, 1445, 1454);
            BigDecimal low14551500 = getMinPriceInWindow(dMinutes, 1455, 1500);

            BigDecimal high09300935 = getMaxPriceInWindow(nextDMinutes, 930, 935);
            BigDecimal high09360940 = getMaxPriceInWindow(nextDMinutes, 936, 940);
            BigDecimal high09410944 = getMaxPriceInWindow(nextDMinutes, 941, 944);
            BigDecimal price0945 = getPriceAt(nextDMinutes, 945);

            TradeSample sample = new TradeSample();
            BigDecimal trigger3 = basePrice.multiply(BUY_DROP_3PCT);
            BigDecimal trigger2 = basePrice.multiply(BUY_DROP_2PCT);
            BigDecimal trigger1 = basePrice.multiply(BUY_DROP_1PCT);

            if (low14351444 != null && low14351444.compareTo(trigger3) <= 0) {
                sample.buyRule = "BUY_DROP_3PCT_AFTER_1435";
                sample.buyPrice = trigger3;
                sample.buyFilled = true;
            } else if (low14451454 != null && low14451454.compareTo(trigger2) <= 0) {
                sample.buyRule = "BUY_DROP_2PCT_AFTER_1445";
                sample.buyPrice = trigger2;
                sample.buyFilled = true;
            } else if (low14551500 != null && low14551500.compareTo(trigger1) <= 0) {
                sample.buyRule = "BUY_DROP_1PCT_AFTER_1455";
                sample.buyPrice = trigger1;
                sample.buyFilled = true;
            } else {
                sample.buyRule = "NOT_FILLED";
                sample.buyFilled = false;
            }

            if (!sample.buyFilled) {
                samples.add(sample);
                continue;
            }

            BigDecimal sellTrigger3 = sample.buyPrice.multiply(SELL_PROFIT_3PCT);
            BigDecimal sellTrigger2 = sample.buyPrice.multiply(SELL_PROFIT_2PCT);
            BigDecimal sellTrigger1 = sample.buyPrice.multiply(SELL_PROFIT_1PCT);

            if (high09300935 != null && high09300935.compareTo(sellTrigger3) >= 0) {
                sample.sellRule = "SELL_PROFIT_3PCT_BEFORE_0935";
                sample.sellPrice = sellTrigger3;
            } else if (high09360940 != null && high09360940.compareTo(sellTrigger2) >= 0) {
                sample.sellRule = "SELL_PROFIT_2PCT_BEFORE_0940";
                sample.sellPrice = sellTrigger2;
            } else if (high09410944 != null && high09410944.compareTo(sellTrigger1) >= 0) {
                sample.sellRule = "SELL_PROFIT_1PCT_BEFORE_0944";
                sample.sellPrice = sellTrigger1;
            } else if (price0945 != null) {
                sample.sellRule = "FORCE_SELL_0945";
                sample.sellPrice = price0945;
            } else {
                continue;
            }

            sample.grossReturnBps = sample.sellPrice.subtract(sample.buyPrice)
                    .divide(sample.buyPrice, 8, RoundingMode.HALF_UP)
                    .multiply(BPS_BASE).setScale(4, RoundingMode.HALF_UP);
            sample.netReturnBps = sample.grossReturnBps.subtract(roundTripCostBps)
                    .setScale(4, RoundingMode.HALF_UP);
            sample.winFlag = sample.netReturnBps.compareTo(BigDecimal.ZERO) > 0;
            samples.add(sample);
        }

        populateStats(snapshot, samples);
    }

    private void populateStats(V3FactorSnapshot snapshot, List<TradeSample> samples) {
        if (samples.isEmpty()) {
            snapshot.setShortSampleCount(0);
            return;
        }

        int totalCount = samples.size();
        snapshot.setShortSampleCount(totalCount);

        List<TradeSample> filledSamples = samples.stream().filter(s -> s.buyFilled).collect(Collectors.toList());
        int filledCount = filledSamples.size();

        long buy3Count = samples.stream().filter(s -> "BUY_DROP_3PCT_AFTER_1435".equals(s.buyRule)).count();
        long buy2Count = samples.stream().filter(s -> "BUY_DROP_2PCT_AFTER_1445".equals(s.buyRule)).count();
        long buy1Count = samples.stream().filter(s -> "BUY_DROP_1PCT_AFTER_1455".equals(s.buyRule)).count();
        long notFilledCount = samples.stream().filter(s -> "NOT_FILLED".equals(s.buyRule)).count();

        snapshot.setBuyFillRate(rate(filledCount, totalCount));
        snapshot.setBuy3PctFillRate(rate(buy3Count, totalCount));
        snapshot.setBuy2PctFillRate(rate(buy2Count, totalCount));
        snapshot.setBuy1PctFillRate(rate(buy1Count, totalCount));
        snapshot.setNotFilledRate(rate(notFilledCount, totalCount));

        if (filledCount > 0) {
            long tp1Count = filledSamples.stream().filter(s -> "SELL_PROFIT_1PCT_BEFORE_0944".equals(s.sellRule)).count();
            long tp2Count = filledSamples.stream().filter(s -> "SELL_PROFIT_2PCT_BEFORE_0940".equals(s.sellRule)).count();
            long tp3Count = filledSamples.stream().filter(s -> "SELL_PROFIT_3PCT_BEFORE_0935".equals(s.sellRule)).count();
            long fsCount = filledSamples.stream().filter(s -> "FORCE_SELL_0945".equals(s.sellRule)).count();

            snapshot.setTakeProfit1PctRate(rate(tp1Count, filledCount));
            snapshot.setTakeProfit2PctRate(rate(tp2Count, filledCount));
            snapshot.setTakeProfit3PctRate(rate(tp3Count, filledCount));
            snapshot.setForceSellRate(rate(fsCount, filledCount));

            List<BigDecimal> netReturns = filledSamples.stream().map(s -> s.netReturnBps).collect(Collectors.toList());
            List<BigDecimal> grossReturns = filledSamples.stream().map(s -> s.grossReturnBps).collect(Collectors.toList());

            BigDecimal avgNetReturnBps = avg(netReturns);
            snapshot.setAvgNetReturnBps(avgNetReturnBps);
            snapshot.setShortAvgNetReturnBps(avgNetReturnBps);
            snapshot.setAvgGrossReturnBps(avg(grossReturns));
            snapshot.setMaxLossBps(netReturns.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));

            List<BigDecimal> tpReturns = filledSamples.stream()
                    .filter(s -> !"FORCE_SELL_0945".equals(s.sellRule))
                    .map(s -> s.netReturnBps).collect(Collectors.toList());
            snapshot.setAvgTakeProfitReturnBps(tpReturns.isEmpty() ? BigDecimal.ZERO : avg(tpReturns));

            List<BigDecimal> fsReturns = filledSamples.stream()
                    .filter(s -> "FORCE_SELL_0945".equals(s.sellRule))
                    .map(s -> s.netReturnBps).collect(Collectors.toList());
            snapshot.setAvgForceSellReturnBps(fsReturns.isEmpty() ? BigDecimal.ZERO : avg(fsReturns));

            long fsLossCount = filledSamples.stream()
                    .filter(s -> "FORCE_SELL_0945".equals(s.sellRule) && !s.winFlag).count();
            long fsAllCount = filledSamples.stream().filter(s -> "FORCE_SELL_0945".equals(s.sellRule)).count();
            snapshot.setForceSellLossRate(fsAllCount > 0 ? rate(fsLossCount, fsAllCount) : BigDecimal.ZERO);

            List<TradeSample> buy3Samples = filledSamples.stream()
                    .filter(s -> "BUY_DROP_3PCT_AFTER_1435".equals(s.buyRule)).collect(Collectors.toList());
            List<TradeSample> buy2Samples = filledSamples.stream()
                    .filter(s -> "BUY_DROP_2PCT_AFTER_1445".equals(s.buyRule)).collect(Collectors.toList());
            List<TradeSample> buy1Samples = filledSamples.stream()
                    .filter(s -> "BUY_DROP_1PCT_AFTER_1455".equals(s.buyRule)).collect(Collectors.toList());

            snapshot.setBuy3PctAvgNetReturnBps(buy3Samples.isEmpty() ? null : avg(buy3Samples.stream().map(s -> s.netReturnBps).collect(Collectors.toList())));
            snapshot.setBuy2PctAvgNetReturnBps(buy2Samples.isEmpty() ? null : avg(buy2Samples.stream().map(s -> s.netReturnBps).collect(Collectors.toList())));
            snapshot.setBuy1PctAvgNetReturnBps(buy1Samples.isEmpty() ? null : avg(buy1Samples.stream().map(s -> s.netReturnBps).collect(Collectors.toList())));

            long winCount = filledSamples.stream().filter(s -> s.winFlag).count();
            snapshot.setShortRawWinRate(rate(winCount, filledCount));

            List<BigDecimal> winReturns = filledSamples.stream().filter(s -> s.winFlag).map(s -> s.netReturnBps).collect(Collectors.toList());
            List<BigDecimal> lossReturns = filledSamples.stream().filter(s -> !s.winFlag).map(s -> s.netReturnBps).collect(Collectors.toList());
            snapshot.setShortAvgWinBps(winReturns.isEmpty() ? BigDecimal.ZERO : avg(winReturns));
            snapshot.setShortAvgLossBps(lossReturns.isEmpty() ? BigDecimal.ZERO : avg(lossReturns).abs());
            if (!lossReturns.isEmpty() && snapshot.getShortAvgWinBps().compareTo(BigDecimal.ZERO) > 0) {
                snapshot.setShortProfitLossRatio(snapshot.getShortAvgWinBps().divide(snapshot.getShortAvgLossBps(), 2, RoundingMode.HALF_UP));
            }

            BigDecimal adjustedWinRate = snapshot.getShortRawWinRate().multiply(BigDecimal.valueOf(filledCount))
                    .add(PRIOR_WIN_RATE.multiply(BigDecimal.valueOf(PRIOR_STRENGTH)))
                    .divide(BigDecimal.valueOf(filledCount + PRIOR_STRENGTH), 4, RoundingMode.HALF_UP);
            snapshot.setShortAdjustedWinRate(adjustedWinRate);
        } else {
            snapshot.setShortAdjustedWinRate(PRIOR_WIN_RATE);
        }
    }

    private BigDecimal getMinPriceInWindow(List<StockMinuteData> minutes, int startTime, int endTime) {
        return minutes.stream()
                .filter(m -> m.getTime() != null && m.getTime() >= startTime && m.getTime() <= endTime)
                .map(StockMinuteData::getLowPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private BigDecimal getMaxPriceInWindow(List<StockMinuteData> minutes, int startTime, int endTime) {
        return minutes.stream()
                .filter(m -> m.getTime() != null && m.getTime() >= startTime && m.getTime() <= endTime)
                .map(StockMinuteData::getHighPrice)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }

    private BigDecimal getPriceAt(List<StockMinuteData> minutes, int time) {
        return minutes.stream()
                .filter(m -> m.getTime() != null && m.getTime() == time)
                .map(StockMinuteData::getPrice)
                .findFirst().orElse(null);
    }

    private BigDecimal avg(List<BigDecimal> values) {
        if (values.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    // ==================== 内部类 ====================

    private static class TradeSample {
        boolean buyFilled;
        String buyRule;
        BigDecimal buyPrice;
        String sellRule;
        BigDecimal sellPrice;
        BigDecimal grossReturnBps;
        BigDecimal netReturnBps;
        boolean winFlag;
    }
}
