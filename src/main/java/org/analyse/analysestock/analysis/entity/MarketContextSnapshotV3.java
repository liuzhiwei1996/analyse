package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * V3 市场环境快照。
 */
@Data
@TableName("market_context_snapshot_v3")
public class MarketContextSnapshotV3 {

    @TableField("trade_date")
    private LocalDate tradeDate;

    // ==================== 市场宽度与收益 ====================
    @TableField("market_breadth_1430")
    private BigDecimal marketBreadth1430;

    @TableField("market_return_1430")
    private BigDecimal marketReturn1430;

    @TableField("market_strong_stock_ratio")
    private BigDecimal marketStrongStockRatio;

    @TableField("market_weak_stock_ratio")
    private BigDecimal marketWeakStockRatio;

    // ==================== 涨跌停统计 ====================
    @TableField("limit_up_count")
    private Integer limitUpCount;

    @TableField("limit_down_count")
    private Integer limitDownCount;

    // ==================== 市场状态 ====================
    @TableField("market_regime")
    private String marketRegime;

    // ==================== 股票统计 ====================
    @TableField("total_valid_stocks")
    private Integer totalValidStocks;

    @TableField("total_filtered_stocks")
    private Integer totalFilteredStocks;

    // ==================== 元数据 ====================
    @TableField("created_at")
    private LocalDateTime createdAt;
}
