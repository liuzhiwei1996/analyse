package org.analyse.analysestock.realtimecandidate.dto;

import lombok.Data;
import org.analyse.analysestock.realtimecandidate.enums.DataQualityFlag;
import org.analyse.analysestock.realtimecandidate.enums.InvalidReason;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * V3 引擎内部因子快照。
 *
 * <p>包含所有 V3 评分所需的原始因子值，以及计算完成后的分项评分。</p>
 */
@Data
public class V3FactorSnapshot {

    // ==================== 基础信息 ====================
    private String stockCode;
    private LocalDate tradeDate;
    private String shortName;
    private String market;
    private String sector;

    // ==================== T日实时因子 ====================
    private BigDecimal price1400;
    private BigDecimal price1430;
    private BigDecimal closePrevious;
    private BigDecimal tailMomentum;
    private BigDecimal intradayLowBefore1430;
    private BigDecimal intradayHighBefore1430;
    private BigDecimal intradayPosition;
    private BigDecimal todayTailAmount;
    private BigDecimal tailAmountRatio;
    private BigDecimal returnTo1430;
    private BigDecimal amountBefore1430;

    // ==================== 日K因子 ====================
    private BigDecimal return5d;
    private BigDecimal return20d;
    private BigDecimal volatility20d;
    private BigDecimal avgAmount20d;
    private BigDecimal avgAmplitude20d;
    private BigDecimal maxDrop20d;
    private BigDecimal position20d;

    // ==================== 短样本统计（来自 ShortSampleCalculatorV3） ====================
    private Integer shortSampleCount;
    private BigDecimal shortRawWinRate;
    private BigDecimal shortAdjustedWinRate;
    private BigDecimal shortAvgNetReturnBps;
    private BigDecimal shortAvgWinBps;
    private BigDecimal shortAvgLossBps;
    private BigDecimal shortProfitLossRatio;

    // 分阶段买卖统计
    private BigDecimal buyFillRate;
    private BigDecimal buy3PctFillRate;
    private BigDecimal buy2PctFillRate;
    private BigDecimal buy1PctFillRate;
    private BigDecimal notFilledRate;

    private BigDecimal takeProfit1PctRate;
    private BigDecimal takeProfit2PctRate;
    private BigDecimal takeProfit3PctRate;
    private BigDecimal forceSellRate;

    private BigDecimal avgTakeProfitReturnBps;
    private BigDecimal avgForceSellReturnBps;
    private BigDecimal avgNetReturnBps;
    private BigDecimal avgGrossReturnBps;
    private BigDecimal maxLossBps;

    private BigDecimal buy3PctAvgNetReturnBps;
    private BigDecimal buy2PctAvgNetReturnBps;
    private BigDecimal buy1PctAvgNetReturnBps;

    private BigDecimal postBuyDrawdownAvg;
    private BigDecimal forceSellLossRate;

    // ==================== 早盘冲高能力 ====================
    private BigDecimal morningHighReturnAvg;
    private BigDecimal morningHighReturnMedian;
    private BigDecimal hit1PctRate;
    private BigDecimal hit2PctRate;
    private BigDecimal hit3PctRate;
    private BigDecimal forceSellAvgReturnBps;
    private BigDecimal morningLiquidity;

    // ==================== 市场/板块环境 ====================
    private BigDecimal marketBreadth;
    private BigDecimal marketReturn1430;
    private String marketRegime;
    private BigDecimal sectorStrength;
    private BigDecimal sectorBreadth;
    private Integer sectorRank;
    private BigDecimal relativeStrength1430;

    // ==================== 风险指标 ====================
    private BigDecimal liquidityRiskScore;
    private BigDecimal gapDownRiskScore;
    private BigDecimal tailSellPressure;

    // ==================== 分项评分（0-1 范围） ====================
    private BigDecimal expectedNetReturnScore;
    private BigDecimal morningBreakoutScore;
    private BigDecimal buyExecutionFitScore;
    private BigDecimal tailStrengthScore;
    private BigDecimal sectorMarketScore;
    private BigDecimal riskControlScore;
    private BigDecimal shortSampleScore;

    // ==================== 元数据 ====================
    private boolean valid = true;
    private InvalidReason invalidReason;
    private DataQualityFlag dataQualityFlag = DataQualityFlag.NORMAL;
    private BigDecimal filterDowngradeMultiplier = BigDecimal.ONE;
}
