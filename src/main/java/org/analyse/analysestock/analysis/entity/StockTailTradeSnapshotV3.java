package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * V3 增强尾盘交易快照。
 *
 * <p>合并了 V2 的 stock_tail_trade_snapshot 和 stock_intraday_execution_snapshot 的字段，
 * 并增加了 T+1 日早盘各阶段最高价和 VWAP。</p>
 */
@Data
@TableName("stock_tail_trade_snapshot_v3")
public class StockTailTradeSnapshotV3 {

    @TableField("stock_code")
    private String stockCode;

    @TableField("trade_date")
    private LocalDate tradeDate;

    // ==================== T日基础价格 ====================
    @TableField("price_1400")
    private BigDecimal price1400;

    @TableField("price_1430")
    private BigDecimal price1430;

    @TableField("high_before_1430")
    private BigDecimal highBefore1430;

    @TableField("low_before_1430")
    private BigDecimal lowBefore1430;

    // ==================== T日尾盘成交 ====================
    @TableField("tail_amount_1400_1430")
    private BigDecimal tailAmount14001430;

    @TableField("tail_volume_1400_1430")
    private Long tailVolume14001430;

    @TableField("amount_before_1430")
    private BigDecimal amountBefore1430;

    @TableField("volume_before_1430")
    private Long volumeBefore1430;

    // ==================== T日尾盘买入窗口 ====================
    @TableField("low_1435_1444")
    private BigDecimal low14351444;

    @TableField("low_1445_1454")
    private BigDecimal low14451454;

    @TableField("low_1455_1500")
    private BigDecimal low14551500;

    // ==================== T日收盘 ====================
    @TableField("close_price")
    private BigDecimal closePrice;

    @TableField("close_amount")
    private BigDecimal closeAmount;

    // ==================== T+1日早盘卖出窗口 ====================
    @TableField("high_0930_0935")
    private BigDecimal high09300935;

    @TableField("high_0936_0940")
    private BigDecimal high09360940;

    @TableField("high_0941_0944")
    private BigDecimal high09410944;

    @TableField("price_0945")
    private BigDecimal price0945;

    @TableField("vwap_0930_0945")
    private BigDecimal vwap09300945;

    @TableField("sell_amount_0930_0945")
    private BigDecimal sellAmount09300945;

    @TableField("sell_volume_0930_0945")
    private Long sellVolume09300945;

    // ==================== 元数据 ====================
    @TableField("valid_flag")
    private Boolean validFlag;

    @TableField("invalid_reason")
    private String invalidReason;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
