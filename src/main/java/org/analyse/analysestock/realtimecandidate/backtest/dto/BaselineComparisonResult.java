package org.analyse.analysestock.realtimecandidate.backtest.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 基线对比结果。
 *
 * <p>对比 TopK 选股与随机选股、BottomK、MiddleK 的表现差异。</p>
 */
@Data
public class BaselineComparisonResult {

    /**
     * 基线类型：TOP_K / RANDOM / BOTTOM_K / MIDDLE_K。
     */
    private String baselineType;

    /**
     * 组合规模。
     */
    private Integer topK;

    /**
     * 成本场景，单位 bps。
     */
    private BigDecimal costBps;

    /**
     * 有效交易日数。
     */
    private Integer tradeDays;

    /**
     * 平均净收益，单位 bps。
     */
    private BigDecimal avgNetReturnBps;

    /**
     * 日胜率。
     */
    private BigDecimal dailyWinRate;

    /**
     * 单票胜率。
     */
    private BigDecimal stockWinRate;

    /**
     * 累计总收益，单位 bps。
     */
    private BigDecimal totalReturnBps;

    /**
     * 最大单日亏损，单位 bps。
     */
    private BigDecimal maxSingleDayLossBps;

    /**
     * 买入成交率。
     */
    private BigDecimal buyFillRate;

    /**
     * 相对 TopK 的超额收益（仅 RANDOM/BOTTOM_K/MIDDLE_K 基线有值）。
     */
    private BigDecimal excessReturnBps;

    /**
     * 随机迭代次数（仅 RANDOM 基线有值）。
     */
    private Integer randomIteration;
}
