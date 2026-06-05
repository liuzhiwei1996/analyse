package org.analyse.analysestock.realtimecandidate.engine;

import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.analyse.analysestock.realtimecandidate.calculator.*;
import org.analyse.analysestock.realtimecandidate.config.CostConfig;
import org.analyse.analysestock.realtimecandidate.config.RealtimeStrategyConfig;
import org.analyse.analysestock.realtimecandidate.dto.*;
import org.analyse.analysestock.realtimecandidate.enums.ConfidenceLevel;
import org.analyse.analysestock.realtimecandidate.enums.InvalidReason;
import org.analyse.analysestock.realtimecandidate.util.PercentileUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class RealtimeCandidateScoreEngine {

    private final FactorCalculator factorCalculator = new FactorCalculator();
    private final DailyTrendCalculator dailyTrendCalculator = new DailyTrendCalculator();
    private final MarketContextCalculator marketContextCalculator = new MarketContextCalculator();
    private final ShortSampleCalculator shortSampleCalculator = new ShortSampleCalculator();
    private final ScoreCalculator scoreCalculator = new ScoreCalculator();

    public List<RealtimeCandidateScoreRecord> calculate(
            LocalDate tradeDate,
            List<DailyBar> dailyBarsDto,
            List<MinuteBar> minuteBarsDto,
            List<SecurityInfo> securityInfosDto,
            RealtimeStrategyConfig strategyConfig,
            CostConfig costConfig
    ) {
        // 适配 DTO 到实体类 (或者直接在内部逻辑中使用 DTO)
        List<StockDataDailyAll> dailyBars = dailyBarsDto.stream().map(d -> {
            StockDataDailyAll daily = new StockDataDailyAll();
            daily.setStockCode(d.getStockCode());
            daily.setTradeDate(d.getTradeDate());
            daily.setOpen(d.getOpen());
            daily.setHighest(d.getHigh());
            daily.setLowest(d.getLow());
            daily.setClose(d.getClose());
            daily.setAmount(d.getAmount());
            daily.setVolume(d.getVolume());
            daily.setClosePrevious(d.getClosePrevious());
            return daily;
        }).collect(Collectors.toList());

        List<StockMinuteData> minuteBars = minuteBarsDto.stream().map(m -> {
            StockMinuteData min = new StockMinuteData();
            try {
                min.setStockCode(Integer.valueOf(m.getStockCode()));
            } catch (Exception e) {
                // 忽略非数字代码或做转换
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

        List<PubStockInfo> securityInfos = securityInfosDto.stream().map(s -> {
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

        return calculateWithEntities(tradeDate, dailyBars, minuteBars, securityInfos, strategyConfig, costConfig);
    }

    public List<RealtimeCandidateScoreRecord> calculateWithEntities(
            LocalDate tradeDate,
            List<StockDataDailyAll> dailyBars,
            List<StockMinuteData> minuteBars,
            List<PubStockInfo> securityInfos,
            RealtimeStrategyConfig strategyConfig,
            CostConfig costConfig
    ) {
        // 1. 构建股票池并初步过滤
        Map<String, List<StockDataDailyAll>> dailyMap = dailyBars.stream()
                .collect(Collectors.groupingBy(StockDataDailyAll::getStockCode));
        Map<LocalDate, List<StockMinuteData>> minuteByDate = minuteBars.stream()
                .filter(m -> m.getTradeDate() != null)
                .collect(Collectors.groupingBy(StockMinuteData::getTradeDate));
        List<StockMinuteData> tMinuteBars = minuteByDate.getOrDefault(tradeDate, Collections.emptyList());

        List<FactorSnapshot> snapshots = new ArrayList<>();
        Map<String, PubStockInfo> infoMap = securityInfos.stream()
                .collect(Collectors.toMap(PubStockInfo::getSymbol, i -> i, (a, b) -> a));

        for (PubStockInfo info : securityInfos) {
            String stockCode = info.getSymbol();
            
            // 过滤规则 13.1
            if (strategyConfig.isExcludeSt() && "1".equals(info.getStStatus())) {
                continue; 
            }
            if (strategyConfig.isExcludeDelisted() && "0".equals(info.getListedStatus())) {
                continue;
            }
            if (strategyConfig.isExcludeNewStock() && info.getListingDate() != null) {
                if (info.getListingDate().plusDays(strategyConfig.getMinListingDays()).isAfter(tradeDate)) {
                    continue;
                }
            }

            List<StockDataDailyAll> sDaily = dailyMap.getOrDefault(stockCode, Collections.emptyList());
            List<StockMinuteData> sTMinute = tMinuteBars.stream()
                    .filter(m -> {
                        if (m.getStockCode() == null) return false;
                        String mCodeStr = m.getStockCode().toString();
                        if (mCodeStr.length() < 6) mCodeStr = String.format("%06d", m.getStockCode());
                        
                        String pureStockCode = stockCode;
                        if (stockCode.length() > 6) {
                            pureStockCode = stockCode.substring(stockCode.length() - 6);
                        } else if (stockCode.length() < 6) {
                            pureStockCode = String.format("%06d", Integer.valueOf(stockCode));
                        }
                        return mCodeStr.equals(pureStockCode);
                    })
                    .collect(Collectors.toList());

            FactorSnapshot snapshot = factorCalculator.calculate(stockCode, tradeDate, sDaily, sTMinute);
            if (!snapshot.isValid()) {
                // log.error("[DEBUG_LOG] Snapshot invalid for stock {}, reason: {}", stockCode, snapshot.getInvalidReason());
                continue;
            }

            // 计算日K趋势因子
            dailyTrendCalculator.calculate(snapshot, sDaily, tradeDate);

            // 过滤低成交额 13.1
            if (snapshot.getAvgAmount20d() != null && snapshot.getAvgAmount20d().compareTo(strategyConfig.getMinDailyAmount()) < 0) {
                continue;
            }
            if (snapshot.getTodayTailAmount() != null && snapshot.getTodayTailAmount().compareTo(strategyConfig.getMinTailAmount()) < 0) {
                continue;
            }

            // 计算短样本统计
            ShortSampleStats shortStats = shortSampleCalculator.calculate(stockCode, minuteBars, sDaily, costConfig, strategyConfig.getVolumeMultiplier());
            snapshot.setShortSampleStats(shortStats);

            snapshots.add(snapshot);
        }

        if (snapshots.isEmpty()) return Collections.emptyList();

        // 2. 计算市场环境
        MarketContext marketContext = marketContextCalculator.calculate(snapshots, securityInfos);

        // 3. 横截面归一化 (百分位排名)
        // tailMomentumScore
        List<BigDecimal> tmRanks = PercentileUtils.calculatePercentileRanks(
                snapshots.stream().map(FactorSnapshot::getTailMomentum).collect(Collectors.toList()));
        // tailVolumeRatioScore
        // 注意：这里需要历史基准，如果没有，暂时用当日截面排名替代或设为0.5
        List<BigDecimal> tvRanks = PercentileUtils.calculatePercentileRanks(
                snapshots.stream().map(FactorSnapshot::getTodayTailAmount).collect(Collectors.toList()));
        // volatilityScore
        List<BigDecimal> volRanks = PercentileUtils.calculatePercentileRanks(
                snapshots.stream().map(FactorSnapshot::getVolatility20d).collect(Collectors.toList()));

        for (int i = 0; i < snapshots.size(); i++) {
            FactorSnapshot s = snapshots.get(i);
            s.setTailMomentumScore(tmRanks.get(i));
            s.setTailVolumeScore(tvRanks.get(i));
            // 波动率得分越高越好，所以用 1 - rank
            if (volRanks.get(i) != null) {
                s.setVolatilityScore(BigDecimal.ONE.subtract(volRanks.get(i)));
            }
            
            // dailyTrendScore 简化逻辑：(return5dRank + return20dRank) / 2
            s.setDailyTrendScore(new BigDecimal("0.5")); // 默认
        }

        // 4. 计算最终评分
        List<RealtimeCandidateScoreRecord> records = new ArrayList<>();
        for (FactorSnapshot s : snapshots) {
            BigDecimal finalScore = scoreCalculator.calculate(s, marketContext, strategyConfig);
            
            RealtimeCandidateScoreRecord record = new RealtimeCandidateScoreRecord();
            record.setTradeDate(tradeDate);
            record.setStockCode(s.getStockCode());
            PubStockInfo info = infoMap.get(s.getStockCode());
            if (info != null) {
                record.setShortName(info.getShortName());
                record.setMarket(info.getMarkets());
                record.setSector(info.getSector());
                
                // relativeStrength = returnTo1430 - sectorStrength
                if (marketContext.getSectorStrength() != null && info.getSector() != null) {
                    BigDecimal ss = marketContext.getSectorStrength().getOrDefault(info.getSector(), BigDecimal.ZERO);
                    record.setRelativeStrength(s.getReturnTo1430().subtract(ss));
                    record.setSectorStrength(ss);
                    record.setSectorBreadth(marketContext.getSectorBreadth().getOrDefault(info.getSector(), BigDecimal.ZERO));
                }
            }
            
            record.setPrice1400(s.getPrice1400());
            record.setPrice1430(s.getPrice1430());
            record.setBuyRefPrice1430(s.getPrice1430());
            record.setTailMomentum(s.getTailMomentum());
            record.setTailAmount1400To1430(s.getTodayTailAmount());
            record.setTailVolumeRatio(s.getTailVolumeRatio());
            record.setIntradayPosition(s.getIntradayPosition());
            record.setReturnTo1430(s.getReturnTo1430());
            record.setReturn5d(s.getReturn5d());
            record.setReturn20d(s.getReturn20d());
            record.setVolatility20d(s.getVolatility20d());
            record.setAvgAmount20d(s.getAvgAmount20d());
            
            record.setMarketBreadth(marketContext.getMarketBreadth());
            record.setMarketReturn1430(marketContext.getMarketReturn1430());
            
            if (s.getShortSampleStats() != null) {
                record.setShortSampleCount(s.getShortSampleStats().getShortSampleCount());
                record.setShortWinRate(s.getShortSampleStats().getShortWinRate());
                record.setShortAvgNetReturnBps(s.getShortSampleStats().getShortAvgNetReturnBps());
            }
            
            record.setTailMomentumScore(s.getTailMomentumScore());
            record.setTailVolumeScore(s.getTailVolumeScore());
            record.setIntradayPositionScore(s.getIntradayPositionScore());
            record.setDailyTrendScore(s.getDailyTrendScore());
            record.setVolatilityScore(s.getVolatilityScore());
            record.setRegimeScore(marketContext.getRegimeScore());
            record.setShortSampleScore(s.getShortSampleScore());
            
            record.setFinalScore(finalScore);
            record.setValidFlag(true);
            record.setDataQualityFlag(s.getDataQualityFlag());
            
            // 置信度
            int sc = record.getShortSampleCount();
            if (sc < 5) record.setConfidenceLevel(ConfidenceLevel.VERY_LOW_CONFIDENCE);
            else if (sc < 10) record.setConfidenceLevel(ConfidenceLevel.LOW_CONFIDENCE);
            else if (sc < 20) record.setConfidenceLevel(ConfidenceLevel.MEDIUM_LOW_CONFIDENCE);
            else record.setConfidenceLevel(ConfidenceLevel.MEDIUM_CONFIDENCE);

            records.add(record);
        }

        // 5. 排序并分配排名
        records.sort(Comparator.comparing(RealtimeCandidateScoreRecord::getFinalScore).reversed()
                .thenComparing(RealtimeCandidateScoreRecord::getStockCode));
        
        for (int i = 0; i < records.size(); i++) {
            records.get(i).setRankNo(i + 1);
        }

        return records;
    }
}
