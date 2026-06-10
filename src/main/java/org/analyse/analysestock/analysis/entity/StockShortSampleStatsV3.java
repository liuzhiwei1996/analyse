package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * V3 短样本统计。
 *
 * <p>基于分阶段买卖规则（而非简单 14:30 买次日 VWAP 卖）统计的历史表现。</p>
 */
@Data
@TableName("stock_short_sample_stats_v3")
public class StockShortSampleStatsV3 {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("stock_code")
    private String stockCode;

    @TableField("trade_date")
    private LocalDate tradeDate;

    // ==================== 买入成交统计 ====================
    @TableField("buy_fill_rate")
    private BigDecimal buyFillRate;

    @TableField("buy_3pct_fill_rate")
    private BigDecimal buy3PctFillRate;

    @TableField("buy_2pct_fill_rate")
    private BigDecimal buy2PctFillRate;

    @TableField("buy_1pct_fill_rate")
    private BigDecimal buy1PctFillRate;

    @TableField("not_filled_rate")
    private BigDecimal notFilledRate;

    // ==================== 止盈统计 ====================
    @TableField("take_profit_1pct_rate")
    private BigDecimal takeProfit1PctRate;

    @TableField("take_profit_2pct_rate")
    private BigDecimal takeProfit2PctRate;

    @TableField("take_profit_3pct_rate")
    private BigDecimal takeProfit3PctRate;

    @TableField("force_sell_rate")
    private BigDecimal forceSellRate;

    // ==================== 收益统计 ====================
    @TableField("avg_net_return_bps")
    private BigDecimal avgNetReturnBps;

    @TableField("avg_gross_return_bps")
    private BigDecimal avgGrossReturnBps;

    @TableField("avg_take_profit_return_bps")
    private BigDecimal avgTakeProfitReturnBps;

    @TableField("avg_force_sell_return_bps")
    private BigDecimal avgForceSellReturnBps;

    @TableField("max_loss_bps")
    private BigDecimal maxLossBps;

    // ==================== 分档收益 ====================
    @TableField("buy_3pct_avg_net_return_bps")
    private BigDecimal buy3PctAvgNetReturnBps;

    @TableField("buy_2pct_avg_net_return_bps")
    private BigDecimal buy2PctAvgNetReturnBps;

    @TableField("buy_1pct_avg_net_return_bps")
    private BigDecimal buy1PctAvgNetReturnBps;

    // ==================== 风险统计 ====================
    @TableField("post_buy_drawdown_avg")
    private BigDecimal postBuyDrawdownAvg;

    @TableField("force_sell_loss_rate")
    private BigDecimal forceSellLossRate;

    // ==================== 贝叶斯修正 ====================
    @TableField("raw_win_rate")
    private BigDecimal rawWinRate;

    @TableField("adjusted_win_rate")
    private BigDecimal adjustedWinRate;

    @TableField("sample_count")
    private Integer sampleCount;

    // ==================== 元数据 ====================
    @TableField("created_at")
    private LocalDateTime createdAt;
}
