package org.analyse.analysestock.realtimecandidate.engine;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.analyse.analysestock.analysis.entity.RealtimeCandidateScoreResultV3;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.analyse.analysestock.realtimecandidate.calculator.v3.*;
import org.analyse.analysestock.realtimecandidate.config.CostConfig;
import org.analyse.analysestock.realtimecandidate.config.RealtimeStrategyConfig;
import org.analyse.analysestock.realtimecandidate.dto.*;
import org.analyse.analysestock.realtimecandidate.enums.DataQualityFlag;
import org.analyse.analysestock.realtimecandidate.enums.InvalidReason;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * V3 实时候选股评分引擎。
 *
 * <p>策略版本：REALTIME_CANDIDATE_EXECUTION_FIT_V3</p>
 *
 * <p>编排完整 V3 评分流程：</p>
 * <ol>
 *   <li>股票池构建 + 硬过滤（V3FilterCalculator）</li>
 *   <li>日K因子计算（DailyTrendCalculatorV3）</li>
 *   <li>实时因子计算（FactorCalculator 复用）</li>
 *   <li>短样本统计计算（ShortSampleCalculatorV3）</li>
 *   <li>市场/板块环境计算</li>
 *   <li>7 大模块评分计算</li>
 *   <li>全市场横截面标准化</li>
 *   <li>最终评分聚合 + 排名</li>
 * </ol>
 */
@Slf4j
public class RealtimeCandidateScoreEngineV3 {

    /** 策略版本标识 */
    public static final String STRATEGY_VERSION = "REALTIME_CANDIDATE_EXECUTION_FIT_V3";

    // 计算器实例
    private final V3FilterCalculator filterCalculator = new V3FilterCalculator();
    private final DailyTrendCalculatorV3 dailyTrendCalculator = new DailyTrendCalculatorV3();
    private final ShortSampleCalculatorV3 shortSampleCalculator = new ShortSampleCalculatorV3();
    private final ExpectedNetReturnCalculator expectedNetReturnCalculator = new ExpectedNetReturnCalculator();
    private final MorningBreakoutCalculator morningBreakoutCalculator = new MorningBreakoutCalculator();
    private final BuyExecutionFitCalculator buyExecutionFitCalculator = new BuyExecutionFitCalculator();
    private final TailStrengthCalculator tailStrengthCalculator = new TailStrengthCalculator();
    private final SectorMarketCalculatorV3 sectorMarketCalculator = new SectorMarketCalculatorV3();
    private final RiskControlCalculator riskControlCalculator = new RiskControlCalculator();
    private final V3ScoreAggregator scoreAggregator = new V3ScoreAggregator();

    /**
     * 使用原始数据计算 V3 评分（快照模式）。
     *
     * @param tradeDate        交易日
     * @param dailyBars         日K数据
     * @param minuteBars        分钟线数据
     * @param securityInfos     股票基础信息
     * @param strategyConfig    策略配置
     * @param costConfig        成本配置
     * @return V3 评分结果列表
     */
    public List<RealtimeCandidateScoreResultV3> calculate(
            LocalDate tradeDate,
            List<DailyBar> dailyBars,
            List<MinuteBar> minuteBars,
            List<SecurityInfo> securityInfos,
            RealtimeStrategyConfig strategyConfig,
            CostConfig costConfig) {

        // 适配 DTO 到实体
        List<StockDataDailyAll> dailyBarEntities = adaptDailyBars(dailyBars);
        List<StockMinuteData> minuteBarEntities = adaptMinuteBars(minuteBars);
        List<PubStockInfo> infoEntities = adaptSecurityInfos(securityInfos);

        return calculateWithEntities(tradeDate, dailyBarEntities, minuteBarEntities, infoEntities, strategyConfig, costConfig);
    }

    /**
     * 使用实体数据计算 V3 评分。
     */
    public List<RealtimeCandidateScoreResultV3> calculateWithEntities(
            LocalDate tradeDate,
            List<StockDataDailyAll> dailyBars,
            List<StockMinuteData> minuteBars,
            List<PubStockInfo> securityInfos,
            RealtimeStrategyConfig strategyConfig,
            CostConfig costConfig) {

        // 1. 数据预处理
        Map<String, List<StockDataDailyAll>> dailyMap = dailyBars.stream()
                .collect(Collectors.groupingBy(StockDataDailyAll::getStockCode));

        Map<LocalDate, List<StockMinuteData>> minuteByDate = minuteBars.stream()
                .filter(m -> m.getTradeDate() != null)
                .collect(Collectors.groupingBy(StockMinuteData::getTradeDate));
        List<StockMinuteData> tMinuteBars = minuteByDate.getOrDefault(tradeDate, Collections.emptyList());
        Map<String, List<StockMinuteData>> minuteByStock = groupMinuteBarsByStockCode(minuteBars);
        Map<String, List<StockMinuteData>> tMinuteByStock = groupMinuteBarsByStockCode(tMinuteBars);

        Map<String, PubStockInfo> infoMap = securityInfos.stream()
                .collect(Collectors.toMap(PubStockInfo::getSymbol, i -> i, (a, b) -> a));

        // 2. 构建股票池 + 硬过滤
        List<V3FactorSnapshot> snapshots = new ArrayList<>();

        for (PubStockInfo info : securityInfos) {
            String stockCode = info.getSymbol();

            // 获取日K和分钟线数据
            List<StockDataDailyAll> sDaily = dailyMap.getOrDefault(stockCode, Collections.emptyList());
            String normalizedStockCode = normalizeStockCode(stockCode);
            List<StockMinuteData> sTMinute = tMinuteByStock.getOrDefault(normalizedStockCode, Collections.emptyList());
            List<StockMinuteData> sMinute = minuteByStock.getOrDefault(normalizedStockCode, Collections.emptyList());

            // 创建因子快照
            V3FactorSnapshot snapshot = createBaseSnapshot(stockCode, tradeDate, info, sTMinute);
            if (!snapshot.isValid()) {
                continue;
            }

            // 日K因子计算
            dailyTrendCalculator.calculate(snapshot, sDaily, tradeDate);

            // 从日K获取昨收价
            if (snapshot.getClosePrevious() == null) {
                StockDataDailyAll prevDayK = sDaily.stream()
                        .filter(d -> d.getTradeDate().isBefore(tradeDate))
                        .max(Comparator.comparing(StockDataDailyAll::getTradeDate))
                        .orElse(null);
                if (prevDayK != null && prevDayK.getCloseForead() != null) {
                    snapshot.setClosePrevious(prevDayK.getCloseForead());
                    // 补充 returnTo1430
                    if (snapshot.getPrice1430() != null && snapshot.getReturnTo1430() == null) {
                        snapshot.setReturnTo1430(snapshot.getPrice1430()
                                .divide(prevDayK.getCloseForead(), 6, RoundingMode.HALF_UP)
                                .subtract(BigDecimal.ONE));
                    }
                }
            }

            // 硬过滤
            if (!filterCalculator.apply(snapshot, info, strategyConfig, tradeDate)) {
                continue;
            }

            // 短样本统计
            shortSampleCalculator.calculateForStockMinutes(snapshot, sMinute, costConfig, tradeDate);

            snapshots.add(snapshot);
        }

        if (snapshots.isEmpty()) {
            return Collections.emptyList();
        }

        // 3. 计算市场/板块环境
        calculateMarketSectorContext(snapshots, infoMap);
        return scoreSnapshots(tradeDate, snapshots);
    }

    public List<RealtimeCandidateScoreResultV3> calculateWithSnapshots(
            LocalDate tradeDate,
            List<V3FactorSnapshot> snapshots,
            RealtimeStrategyConfig strategyConfig,
            CostConfig costConfig) {

        if (CollectionUtils.isEmpty(snapshots)) {
            return Collections.emptyList();
        }

        List<V3FactorSnapshot> validSnapshots = snapshots.stream()
                .filter(V3FactorSnapshot::isValid)
                .collect(Collectors.toList());
        if (validSnapshots.isEmpty()) {
            return Collections.emptyList();
        }

        boolean missingMarketContext = validSnapshots.stream().anyMatch(s -> s.getMarketBreadth() == null);
        if (missingMarketContext) {
            calculateMarketSectorContext(validSnapshots, Collections.emptyMap());
        }

        return scoreSnapshots(tradeDate, validSnapshots);
    }

    private List<RealtimeCandidateScoreResultV3> scoreSnapshots(
            LocalDate tradeDate,
            List<V3FactorSnapshot> snapshots) {

        // 检查全市场不出候选
        if (!snapshots.isEmpty()) {
            V3FactorSnapshot first = snapshots.get(0);
            if (sectorMarketCalculator.shouldSuppressAll(first.getMarketBreadth())) {
                log.info("V3: marketBreadth < 20%, suppress all candidates for date={}", tradeDate);
                return Collections.emptyList();
            }
        }
        BigDecimal marketDiscount = snapshots.isEmpty() ? BigDecimal.ONE
                : sectorMarketCalculator.getWeakMarketDiscount(snapshots.get(0).getMarketBreadth());

        // 4. 计算各分项评分
        for (V3FactorSnapshot snapshot : snapshots) {
            // 预期净收益评分
            snapshot.setExpectedNetReturnScore(expectedNetReturnCalculator.calculate(snapshot));

            // 早盘冲高能力评分
            morningBreakoutCalculator.populateFields(snapshot);
            snapshot.setMorningBreakoutScore(morningBreakoutCalculator.calculate(snapshot));

            // 买入成交适配度评分
            snapshot.setBuyExecutionFitScore(buyExecutionFitCalculator.calculate(snapshot));

            // 尾盘强度评分
            snapshot.setTailStrengthScore(tailStrengthCalculator.calculate(snapshot));

            // 板块/市场环境评分
            snapshot.setSectorMarketScore(sectorMarketCalculator.calculate(snapshot));

            // 风险控制评分
            snapshot.setRiskControlScore(riskControlCalculator.calculate(snapshot));

            // 短样本稳定性评分
            snapshot.setShortSampleScore(scoreAggregator.calculateShortSampleScore(snapshot));
        }

        // 5. 全市场横截面标准化
        scoreAggregator.crossSectionalNormalize(snapshots);

        // 6. 最终评分聚合 + 排名
        List<RealtimeCandidateScoreResultV3> records = new ArrayList<>();
        for (V3FactorSnapshot snapshot : snapshots) {
            BigDecimal finalScore = scoreAggregator.calculateFinalScore(snapshot, marketDiscount);
            String confidenceLevel = scoreAggregator.calculateConfidenceLevel(
                    snapshot.getShortSampleCount() != null ? snapshot.getShortSampleCount() : 0);
            ScoreExplanation explanation = scoreAggregator.explain(snapshot);

            RealtimeCandidateScoreResultV3 record = buildRecord(snapshot, finalScore, confidenceLevel, explanation, tradeDate);
            records.add(record);
        }

        // 7. 按 finalScore 降序排序并分配排名
        records.sort((r1, r2) -> {
            BigDecimal s1 = r1.getFinalScore() != null ? r1.getFinalScore() : BigDecimal.ZERO;
            BigDecimal s2 = r2.getFinalScore() != null ? r2.getFinalScore() : BigDecimal.ZERO;
            int cmp = s2.compareTo(s1);
            if (cmp != 0) return cmp;
            String c1 = r1.getStockCode() != null ? r1.getStockCode() : "";
            String c2 = r2.getStockCode() != null ? r2.getStockCode() : "";
            return c1.compareTo(c2);
        });
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setRankNo(i + 1);
        }

        return records;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 创建基础因子快照（从分钟线提取 14:00 和 14:30 价格等）。
     */
    private V3FactorSnapshot createBaseSnapshot(String stockCode, LocalDate tradeDate,
                                                 PubStockInfo info, List<StockMinuteData> tMinuteBars) {
        V3FactorSnapshot snapshot = new V3FactorSnapshot();
        snapshot.setStockCode(stockCode);
        snapshot.setTradeDate(tradeDate);
        snapshot.setShortName(info.getShortName());
        snapshot.setMarket(info.getMarkets());
        snapshot.setSector(info.getSector());

        // 提取 14:00 和 14:30 价格
        StockMinuteData min1400 = tMinuteBars.stream()
                .filter(m -> m.getTime() != null && m.getTime() == 1400).findFirst().orElse(null);
        StockMinuteData min1430 = tMinuteBars.stream()
                .filter(m -> m.getTime() != null && m.getTime() == 1430).findFirst().orElse(null);

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

        // tailMomentum
        if (p1400.compareTo(BigDecimal.ZERO) > 0) {
            snapshot.setTailMomentum(p1430.divide(p1400, 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
        }

        // intradayPosition
        BigDecimal low = tMinuteBars.stream()
                .map(StockMinuteData::getLowPrice).filter(Objects::nonNull)
                .min(BigDecimal::compareTo).orElse(p1430);
        BigDecimal high = tMinuteBars.stream()
                .map(StockMinuteData::getHighPrice).filter(Objects::nonNull)
                .max(BigDecimal::compareTo).orElse(p1430);
        snapshot.setIntradayLowBefore1430(low);
        snapshot.setIntradayHighBefore1430(high);

        if (high.compareTo(low) == 0) {
            snapshot.setIntradayPosition(new BigDecimal("0.5"));
            snapshot.setDataQualityFlag(DataQualityFlag.FLAT_INTRADAY_RANGE);
        } else {
            snapshot.setIntradayPosition(p1430.subtract(low).divide(high.subtract(low), 4, RoundingMode.HALF_UP));
        }

        // todayTailAmount（14:00-14:30）
        BigDecimal tailAmount = tMinuteBars.stream()
                .filter(m -> m.getTime() != null && m.getTime() >= 1400 && m.getTime() <= 1430)
                .map(StockMinuteData::getMinuteAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        snapshot.setTodayTailAmount(tailAmount);

        // amountBefore1430
        BigDecimal amountBefore1430 = tMinuteBars.stream()
                .filter(m -> m.getTime() != null && m.getTime() <= 1430)
                .map(StockMinuteData::getMinuteAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        snapshot.setAmountBefore1430(amountBefore1430);

        // returnTo1430（如果有昨收）
        if (snapshot.getClosePrevious() != null && snapshot.getClosePrevious().compareTo(BigDecimal.ZERO) > 0) {
            snapshot.setReturnTo1430(p1430.divide(snapshot.getClosePrevious(), 6, RoundingMode.HALF_UP).subtract(BigDecimal.ONE));
        }

        return snapshot;
    }

    /**
     * 计算市场/板块环境。
     */
    private void calculateMarketSectorContext(List<V3FactorSnapshot> snapshots, Map<String, PubStockInfo> infoMap) {
        if (snapshots.isEmpty()) return;

        // 市场宽度
        long upCount = snapshots.stream()
                .filter(s -> s.getClosePrevious() != null && s.getPrice1430() != null
                        && s.getPrice1430().compareTo(s.getClosePrevious()) > 0)
                .count();
        BigDecimal marketBreadth = BigDecimal.valueOf(upCount)
                .divide(BigDecimal.valueOf(snapshots.size()), 4, RoundingMode.HALF_UP);

        // 市场平均涨幅
        BigDecimal totalReturn = snapshots.stream()
                .map(s -> s.getReturnTo1430() != null ? s.getReturnTo1430() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal marketReturn = totalReturn.divide(BigDecimal.valueOf(snapshots.size()), 6, RoundingMode.HALF_UP);

        // 市场状态
        String marketRegime = sectorMarketCalculator.determineMarketRegime(marketBreadth);

        // 板块环境
        Map<String, List<V3FactorSnapshot>> sectorGroups = snapshots.stream()
                .filter(s -> s.getSector() != null)
                .collect(Collectors.groupingBy(V3FactorSnapshot::getSector));

        Map<String, BigDecimal> sectorStrengthMap = new HashMap<>();
        Map<String, BigDecimal> sectorBreadthMap = new HashMap<>();
        Map<String, Integer> sectorStockCountMap = new HashMap<>();

        for (Map.Entry<String, List<V3FactorSnapshot>> entry : sectorGroups.entrySet()) {
            List<V3FactorSnapshot> sectorStocks = entry.getValue();
            BigDecimal sectorTotalReturn = sectorStocks.stream()
                    .map(s -> s.getReturnTo1430() != null ? s.getReturnTo1430() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            sectorStrengthMap.put(entry.getKey(),
                    sectorTotalReturn.divide(BigDecimal.valueOf(sectorStocks.size()), 6, RoundingMode.HALF_UP));

            long sectorUpCount = sectorStocks.stream()
                    .filter(s -> s.getClosePrevious() != null && s.getPrice1430() != null
                            && s.getPrice1430().compareTo(s.getClosePrevious()) > 0)
                    .count();
            sectorBreadthMap.put(entry.getKey(),
                    BigDecimal.valueOf(sectorUpCount).divide(BigDecimal.valueOf(sectorStocks.size()), 4, RoundingMode.HALF_UP));
            sectorStockCountMap.put(entry.getKey(), sectorStocks.size());
        }

        // 板块排名（按强度降序）
        List<Map.Entry<String, BigDecimal>> sortedSectors = new ArrayList<>(sectorStrengthMap.entrySet());
        sortedSectors.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        Map<String, Integer> sectorRankMap = new HashMap<>();
        for (int i = 0; i < sortedSectors.size(); i++) {
            sectorRankMap.put(sortedSectors.get(i).getKey(), i + 1);
        }

        // 填充到每个快照
        for (V3FactorSnapshot snapshot : snapshots) {
            snapshot.setMarketBreadth(marketBreadth);
            snapshot.setMarketReturn1430(marketReturn);
            snapshot.setMarketRegime(marketRegime);

            String sector = snapshot.getSector();
            if (sector != null) {
                snapshot.setSectorStrength(sectorStrengthMap.getOrDefault(sector, BigDecimal.ZERO));
                snapshot.setSectorBreadth(sectorBreadthMap.getOrDefault(sector, BigDecimal.ZERO));
                snapshot.setSectorRank(sectorRankMap.getOrDefault(sector, 999));

                // 相对强度
                BigDecimal ss = snapshot.getSectorStrength();
                if (snapshot.getReturnTo1430() != null && ss != null) {
                    snapshot.setRelativeStrength1430(snapshot.getReturnTo1430().subtract(ss));
                }
            }
        }
    }

    /**
     * 构建输出记录。
     */
    private RealtimeCandidateScoreResultV3 buildRecord(
            V3FactorSnapshot snapshot, BigDecimal finalScore,
            String confidenceLevel, ScoreExplanation explanation,
            LocalDate tradeDate) {

        RealtimeCandidateScoreResultV3 record = new RealtimeCandidateScoreResultV3();
        record.setTradeDate(tradeDate);
        record.setStockCode(snapshot.getStockCode());
        record.setShortName(snapshot.getShortName());
        record.setMarket(snapshot.getMarket());
        record.setSector(snapshot.getSector());

        // 实时因子
        record.setPrice1400(snapshot.getPrice1400());
        record.setPrice1430(snapshot.getPrice1430());
        record.setReturnTo1430(snapshot.getReturnTo1430());
        record.setTailMomentum(snapshot.getTailMomentum());
        record.setTailAmount14001430(snapshot.getTodayTailAmount());
        record.setTailAmountRatio(snapshot.getTailAmountRatio());
        record.setIntradayPosition(snapshot.getIntradayPosition());
        record.setRelativeStrength1430(snapshot.getRelativeStrength1430());
        record.setAmountBefore1430(snapshot.getAmountBefore1430());

        // 日K因子
        record.setReturn5d(snapshot.getReturn5d());
        record.setReturn20d(snapshot.getReturn20d());
        record.setVolatility20d(snapshot.getVolatility20d());
        record.setAvgAmount20d(snapshot.getAvgAmount20d());
        record.setAvgAmplitude20d(snapshot.getAvgAmplitude20d());
        record.setMaxDrop20d(snapshot.getMaxDrop20d());
        record.setPosition20d(snapshot.getPosition20d());

        // 早盘冲高能力
        record.setMorningHighReturnAvg(snapshot.getMorningHighReturnAvg());
        record.setMorningHighReturnMedian(snapshot.getMorningHighReturnMedian());
        record.setHit1PctRate(snapshot.getHit1PctRate());
        record.setHit2PctRate(snapshot.getHit2PctRate());
        record.setHit3PctRate(snapshot.getHit3PctRate());
        record.setForceSellRate(snapshot.getForceSellRate());
        record.setForceSellAvgReturnBps(snapshot.getForceSellAvgReturnBps());

        // 买入适配
        record.setBuyFillRate(snapshot.getBuyFillRate());
        record.setBuy3PctFillRate(snapshot.getBuy3PctFillRate());
        record.setBuy2PctFillRate(snapshot.getBuy2PctFillRate());
        record.setBuy1PctFillRate(snapshot.getBuy1PctFillRate());
        record.setBuy3PctAvgNetReturnBps(snapshot.getBuy3PctAvgNetReturnBps());
        record.setBuy2PctAvgNetReturnBps(snapshot.getBuy2PctAvgNetReturnBps());
        record.setBuy1PctAvgNetReturnBps(snapshot.getBuy1PctAvgNetReturnBps());
        record.setNotFilledRate(snapshot.getNotFilledRate());

        // 风险
        record.setPostBuyDrawdownAvg(snapshot.getPostBuyDrawdownAvg());
        record.setForceSellLossRate(snapshot.getForceSellLossRate());
        record.setMaxLossBps(snapshot.getMaxLossBps());
        record.setLiquidityRiskScore(snapshot.getLiquidityRiskScore());
        record.setGapDownRiskScore(snapshot.getGapDownRiskScore());

        // 短样本
        record.setShortSampleCount(snapshot.getShortSampleCount());
        record.setShortWinRate(snapshot.getShortRawWinRate());
        record.setShortAdjustedWinRate(snapshot.getShortAdjustedWinRate());
        record.setShortAvgNetReturnBps(snapshot.getShortAvgNetReturnBps());
        record.setShortAvgWinBps(snapshot.getShortAvgWinBps());
        record.setShortAvgLossBps(snapshot.getShortAvgLossBps());
        record.setShortProfitLossRatio(snapshot.getShortProfitLossRatio());

        // 分项评分
        record.setExpectedNetReturnScore(snapshot.getExpectedNetReturnScore());
        record.setMorningBreakoutScore(snapshot.getMorningBreakoutScore());
        record.setBuyExecutionFitScore(snapshot.getBuyExecutionFitScore());
        record.setTailStrengthScore(snapshot.getTailStrengthScore());
        record.setSectorMarketScore(snapshot.getSectorMarketScore());
        record.setRiskControlScore(snapshot.getRiskControlScore());
        record.setShortSampleScore(snapshot.getShortSampleScore());

        // 市场/板块
        record.setMarketBreadth(snapshot.getMarketBreadth());
        record.setMarketReturn1430(snapshot.getMarketReturn1430());
        record.setMarketRegime(snapshot.getMarketRegime());
        record.setSectorStrength(snapshot.getSectorStrength());
        record.setSectorBreadth(snapshot.getSectorBreadth());
        record.setSectorRank(snapshot.getSectorRank());

        // 总分与元数据
        record.setFinalScore(finalScore);
        record.setConfidenceLevel(confidenceLevel);
        record.setDataQualityFlag(snapshot.getDataQualityFlag() != null ? snapshot.getDataQualityFlag().name() : DataQualityFlag.NORMAL.name());
        record.setValidFlag(snapshot.isValid());
        record.setInvalidReason(snapshot.getInvalidReason() != null ? snapshot.getInvalidReason().name() : null);
        record.setStrategyVersion(STRATEGY_VERSION);
        record.setScoreExplanation(JSON.toJSONString(explanation));
        record.setCreatedAt(LocalDateTime.now());

        return record;
    }

    /**
     * 过滤匹配某只股票的分钟线。
     */
    private Map<String, List<StockMinuteData>> groupMinuteBarsByStockCode(List<StockMinuteData> minuteBars) {
        if (CollectionUtils.isEmpty(minuteBars)) return Collections.emptyMap();
        return minuteBars.stream()
                .filter(m -> m.getStockCode() != null)
                .collect(Collectors.groupingBy(m -> normalizeStockCode(m.getStockCode())));
    }

    private String normalizeStockCode(Integer stockCode) {
        return String.format("%06d", stockCode);
    }

    private String normalizeStockCode(String stockCode) {
        if (stockCode == null) return "";
        String pureStockCode = stockCode.length() > 6 ? stockCode.substring(stockCode.length() - 6) : stockCode;
        if (pureStockCode.length() < 6) {
            try {
                return String.format("%06d", Integer.parseInt(pureStockCode));
            } catch (NumberFormatException e) {
                return pureStockCode;
            }
        }
        return pureStockCode;
    }

    private List<StockMinuteData> filterMinuteBars(List<StockMinuteData> tMinuteBars, String stockCode) {
        return tMinuteBars.stream()
                .filter(m -> {
                    if (m.getStockCode() == null) return false;
                    String mCodeStr = m.getStockCode().toString();
                    if (mCodeStr.length() < 6) mCodeStr = String.format("%06d", m.getStockCode());
                    String pureStockCode = stockCode.length() > 6 ? stockCode.substring(stockCode.length() - 6) : stockCode;
                    if (pureStockCode.length() < 6) pureStockCode = String.format("%06d", Integer.parseInt(stockCode));
                    return mCodeStr.equals(pureStockCode);
                })
                .collect(Collectors.toList());
    }

    // ==================== DTO 适配方法 ====================

    private List<StockDataDailyAll> adaptDailyBars(List<DailyBar> dailyBars) {
        if (CollectionUtils.isEmpty(dailyBars)) return Collections.emptyList();
        return dailyBars.stream().map(d -> {
            StockDataDailyAll daily = new StockDataDailyAll();
            daily.setStockCode(d.getStockCode());
            daily.setTradeDate(d.getTradeDate());
            daily.setOpen(d.getOpen());
            daily.setHighest(d.getHigh());
            daily.setLowest(d.getLow());
            daily.setClose(d.getClose());
            daily.setCloseForead(d.getClose());
            daily.setAmount(d.getAmount());
            daily.setVolume(d.getVolume());
            daily.setClosePrevious(d.getClosePrevious());
            return daily;
        }).collect(Collectors.toList());
    }

    private List<StockMinuteData> adaptMinuteBars(List<MinuteBar> minuteBars) {
        if (CollectionUtils.isEmpty(minuteBars)) return Collections.emptyList();
        return minuteBars.stream().map(m -> {
            StockMinuteData min = new StockMinuteData();
            try {
                min.setStockCode(Integer.valueOf(m.getStockCode()));
            } catch (Exception e) {
                // ignore
            }
            min.setTradeDate(m.getTradeDate());
            min.setTime(m.getTime());
            min.setPrice(m.getPrice());
            min.setMinuteVolume(m.getVolume());
            min.setMinuteAmount(m.getAmount());
            min.setHighPrice(m.getHigh());
            min.setLowPrice(m.getLow());
            return min;
        }).collect(Collectors.toList());
    }

    private List<PubStockInfo> adaptSecurityInfos(List<SecurityInfo> securityInfos) {
        if (CollectionUtils.isEmpty(securityInfos)) return Collections.emptyList();
        return securityInfos.stream().map(s -> {
            PubStockInfo info = new PubStockInfo();
            info.setSymbol(s.getSymbol());
            info.setShortName(s.getShortName());
            info.setListingDate(s.getListingDate());
            info.setStStatus(s.getStStatus());
            info.setListedStatus(s.getListedStatus());
            info.setSector(s.getSector());
            info.setMarkets(s.getMarkets());
            return info;
        }).collect(Collectors.toList());
    }
}
