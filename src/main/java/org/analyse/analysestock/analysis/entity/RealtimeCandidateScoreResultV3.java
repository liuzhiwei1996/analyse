package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * V3 实时候选股评分落库结果。
 *
 * <p>策略版本：REALTIME_CANDIDATE_EXECUTION_FIT_V3</p>
 * <p>包含 7 大评分模块的所有因子和分项评分。</p>
 */
@Data
@TableName("realtime_candidate_score_result_v3")
public class RealtimeCandidateScoreResultV3 {

    // ==================== 基础字段 ====================
    @TableField("trade_date")
    private LocalDate tradeDate;

    @TableField("stock_code")
    private String stockCode;

    @TableField("short_name")
    private String shortName;

    @TableField("market")
    private String market;

    @TableField("sector")
    private String sector;

    // ==================== 实时因子字段 ====================
    @TableField("price_1400")
    private BigDecimal price1400;

    @TableField("price_1430")
    private BigDecimal price1430;

    @TableField("return_to_1430")
    private BigDecimal returnTo1430;

    @TableField("tail_momentum")
    private BigDecimal tailMomentum;

    @TableField("tail_amount_1400_1430")
    private BigDecimal tailAmount14001430;

    @TableField("tail_amount_ratio")
    private BigDecimal tailAmountRatio;

    @TableField("intraday_position")
    private BigDecimal intradayPosition;

    @TableField("relative_strength_1430")
    private BigDecimal relativeStrength1430;

    @TableField("amount_before_1430")
    private BigDecimal amountBefore1430;

    // ==================== 日K因子字段 ====================
    @TableField("return_5d")
    private BigDecimal return5d;

    @TableField("return_20d")
    private BigDecimal return20d;

    @TableField("volatility_20d")
    private BigDecimal volatility20d;

    @TableField("avg_amount_20d")
    private BigDecimal avgAmount20d;

    @TableField("avg_amplitude_20d")
    private BigDecimal avgAmplitude20d;

    @TableField("max_drop_20d")
    private BigDecimal maxDrop20d;

    @TableField("position_20d")
    private BigDecimal position20d;

    // ==================== 早盘冲高能力字段 ====================
    @TableField("morning_high_return_avg")
    private BigDecimal morningHighReturnAvg;

    @TableField("morning_high_return_median")
    private BigDecimal morningHighReturnMedian;

    @TableField("hit_1pct_rate")
    private BigDecimal hit1PctRate;

    @TableField("hit_2pct_rate")
    private BigDecimal hit2PctRate;

    @TableField("hit_3pct_rate")
    private BigDecimal hit3PctRate;

    @TableField("force_sell_rate")
    private BigDecimal forceSellRate;

    @TableField("force_sell_avg_return_bps")
    private BigDecimal forceSellAvgReturnBps;

    // ==================== 买入适配字段 ====================
    @TableField("buy_fill_rate")
    private BigDecimal buyFillRate;

    @TableField("buy_3pct_fill_rate")
    private BigDecimal buy3PctFillRate;

    @TableField("buy_2pct_fill_rate")
    private BigDecimal buy2PctFillRate;

    @TableField("buy_1pct_fill_rate")
    private BigDecimal buy1PctFillRate;

    @TableField("buy_3pct_avg_net_return_bps")
    private BigDecimal buy3PctAvgNetReturnBps;

    @TableField("buy_2pct_avg_net_return_bps")
    private BigDecimal buy2PctAvgNetReturnBps;

    @TableField("buy_1pct_avg_net_return_bps")
    private BigDecimal buy1PctAvgNetReturnBps;

    @TableField("not_filled_rate")
    private BigDecimal notFilledRate;

    // ==================== 风险字段 ====================
    @TableField("post_buy_drawdown_avg")
    private BigDecimal postBuyDrawdownAvg;

    @TableField("force_sell_loss_rate")
    private BigDecimal forceSellLossRate;

    @TableField("max_loss_bps")
    private BigDecimal maxLossBps;

    @TableField("liquidity_risk_score")
    private BigDecimal liquidityRiskScore;

    @TableField("gap_down_risk_score")
    private BigDecimal gapDownRiskScore;

    // ==================== 短样本统计 ====================
    @TableField("short_sample_count")
    private Integer shortSampleCount;

    @TableField("short_win_rate")
    private BigDecimal shortWinRate;

    @TableField("short_adjusted_win_rate")
    private BigDecimal shortAdjustedWinRate;

    @TableField("short_avg_net_return_bps")
    private BigDecimal shortAvgNetReturnBps;

    @TableField("short_avg_win_bps")
    private BigDecimal shortAvgWinBps;

    @TableField("short_avg_loss_bps")
    private BigDecimal shortAvgLossBps;

    @TableField("short_profit_loss_ratio")
    private BigDecimal shortProfitLossRatio;

    // ==================== 分项评分字段 ====================
    @TableField("expected_net_return_score")
    private BigDecimal expectedNetReturnScore;

    @TableField("morning_breakout_score")
    private BigDecimal morningBreakoutScore;

    @TableField("buy_execution_fit_score")
    private BigDecimal buyExecutionFitScore;

    @TableField("tail_strength_score")
    private BigDecimal tailStrengthScore;

    @TableField("sector_market_score")
    private BigDecimal sectorMarketScore;

    @TableField("risk_control_score")
    private BigDecimal riskControlScore;

    @TableField("short_sample_score")
    private BigDecimal shortSampleScore;

    // ==================== 市场/板块环境 ====================
    @TableField("market_breadth")
    private BigDecimal marketBreadth;

    @TableField("market_return_1430")
    private BigDecimal marketReturn1430;

    @TableField("market_regime")
    private String marketRegime;

    @TableField("sector_strength")
    private BigDecimal sectorStrength;

    @TableField("sector_breadth")
    private BigDecimal sectorBreadth;

    @TableField("sector_rank")
    private Integer sectorRank;

    // ==================== 总分与排名 ====================
    @TableField("final_score")
    private BigDecimal finalScore;

    @TableField("rank_no")
    private Integer rankNo;

    // ==================== 元数据 ====================
    @TableField("confidence_level")
    private String confidenceLevel;

    @TableField("data_quality_flag")
    private String dataQualityFlag;

    @TableField("valid_flag")
    private Boolean validFlag;

    @TableField("invalid_reason")
    private String invalidReason;

    @TableField("strategy_version")
    private String strategyVersion;

    @TableField("score_explanation")
    private String scoreExplanation;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
