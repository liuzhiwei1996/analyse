package org.analyse.analysestock.realtimecandidate.backtest.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
/**
 * TopK 汇总指标响应对象。
 */
public class BacktestTopKResult {

    /**
     * TopK 组合规模。
     */
    private Integer topK;

    /**
     * 成本场景，单位 bps。
     */
    private BigDecimal costBps;

    /**
     * 当前组合有效回测交易日数。
     */
    private Integer tradeDays;

    /**
     * 日组合平均净收益，单位 bps。
     */
    private BigDecimal avgNetReturnBps;

    /**
     * 日组合胜率。
     */
    private BigDecimal dailyWinRate;

    /**
     * 单票胜率。
     */
    private BigDecimal stockWinRate;

    /**
     * 简单累计总收益，单位 bps。
     */
    private BigDecimal totalReturnBps;

    /**
     * 最大单日亏损，单位 bps。
     */
    private BigDecimal maxSingleDayLossBps;

    /**
     * 平均每日有效选股数量。
     */
    private BigDecimal avgSelectedCount;
}
