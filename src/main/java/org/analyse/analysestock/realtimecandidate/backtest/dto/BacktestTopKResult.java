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

    /**
     * 候选股票总数。
     */
    private Integer candidateCount;

    /**
     * 实际买入股票总数。
     */
    private Integer boughtCount;

    /**
     * 买入成交率。
     */
    private BigDecimal buyFillRate;

    /**
     * 3% 档买入数量。
     */
    private Integer buy3pctCount;

    /**
     * 2% 档买入数量。
     */
    private Integer buy2pctCount;

    /**
     * 1% 档买入数量。
     */
    private Integer buy1pctCount;

    /**
     * 3% 止盈卖出数量。
     */
    private Integer sell3pctCount;

    /**
     * 2% 止盈卖出数量。
     */
    private Integer sell2pctCount;

    /**
     * 1% 止盈卖出数量。
     */
    private Integer sell1pctCount;

    /**
     * 09:45 强制卖出数量。
     */
    private Integer forceSell0945Count;
}
