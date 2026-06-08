package org.analyse.analysestock.realtimecandidate.dto;

import org.analyse.analysestock.realtimecandidate.enums.DataQualityFlag;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class RealtimeFactorSnapshot {
    private LocalDate tradeDate;
    private String stockCode;
    private String shortName;
    private String market;
    private String sector;

    private BigDecimal price1400;
    private BigDecimal price1430;

    private BigDecimal tailMomentum;
    private BigDecimal tailAmount14001430;
    private BigDecimal tailVolumeRatio;

    private BigDecimal intradayPosition;
    private BigDecimal amountBefore1430;

    private BigDecimal returnTo1430;
    private BigDecimal relativeStrength;

    private BigDecimal return5d;
    private BigDecimal return20d;
    private BigDecimal volatility20d;
    private BigDecimal avgAmount20d;

    private BigDecimal marketBreadth1430;
    private BigDecimal marketReturn1430;
    private BigDecimal sectorStrength1430;
    private BigDecimal sectorBreadth1430;

    private Integer shortSampleCount;
    private BigDecimal shortWinRate;
    private BigDecimal shortAvgNetReturnBps;

    private BigDecimal tailMomentumScore;
    private BigDecimal tailVolumeScore;
    private BigDecimal intradayPositionScore;
    private BigDecimal dailyTrendScore;
    private BigDecimal volatilityScore;
    private BigDecimal regimeScore;
    private BigDecimal shortSampleScore;

    private Boolean validFlag;
    private String invalidReason;
    private BigDecimal confidenceLevel;
    private DataQualityFlag dataQualityFlag;
}
