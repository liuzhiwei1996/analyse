package org.analyse.analysestock.realtimecandidate.dto;

import lombok.Data;
import org.analyse.analysestock.realtimecandidate.enums.DataQualityFlag;
import org.analyse.analysestock.realtimecandidate.enums.InvalidReason;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class FactorSnapshot {
    private String stockCode;
    private LocalDate tradeDate;

    private BigDecimal price1400;
    private BigDecimal price1430;
    private BigDecimal closePrevious;

    // 实时因子
    private BigDecimal tailMomentum;
    private BigDecimal intradayLowBefore1430;
    private BigDecimal intradayHighBefore1430;
    private BigDecimal intradayPosition;
    private BigDecimal todayTailAmount;
    private BigDecimal tailVolumeRatio;
    private BigDecimal returnTo1430;

    // 日K因子
    private BigDecimal return5d;
    private BigDecimal return20d;
    private BigDecimal volatility20d;
    private BigDecimal avgAmount20d;

    // 短样本统计
    private ShortSampleStats shortSampleStats;

    // 评分项 (原始百分位或原始得分)
    private BigDecimal tailMomentumScore;
    private BigDecimal tailVolumeScore;
    private BigDecimal intradayPositionScore;
    private BigDecimal dailyTrendScore;
    private BigDecimal volatilityScore;
    private BigDecimal shortSampleScore;

    private boolean valid = true;
    private InvalidReason invalidReason;
    private DataQualityFlag dataQualityFlag = DataQualityFlag.NORMAL;
}
