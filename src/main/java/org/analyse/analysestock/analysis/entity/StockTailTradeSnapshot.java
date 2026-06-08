package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stock_tail_trade_snapshot")
public class StockTailTradeSnapshot {
    @TableField("stock_code")
    private String stockCode;
    @TableField("trade_date")
    private LocalDate tradeDate;

    @TableField("price_1400")
    private BigDecimal price1400;
    @TableField("price_1430")
    private BigDecimal price1430;

    @TableField("high_before_1430")
    private BigDecimal highBefore1430;
    @TableField("low_before_1430")
    private BigDecimal lowBefore1430;

    @TableField("tail_amount_1400_1430")
    private BigDecimal tailAmount14001430;
    @TableField("tail_volume_1400_1430")
    private Long tailVolume14001430;

    @TableField("amount_before_1430")
    private BigDecimal amountBefore1430;
    @TableField("volume_before_1430")
    private Long volumeBefore1430;

    @TableField("sell_amount_0930_0945")
    private BigDecimal sellAmount09300945;
    @TableField("sell_volume_0930_0945")
    private Long sellVolume09300945;
    @TableField("sell_vwap_0930_0945")
    private BigDecimal sellVwap09300945;

    @TableField("valid_flag")
    private Boolean validFlag;
    @TableField("invalid_reason")
    private String invalidReason;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
