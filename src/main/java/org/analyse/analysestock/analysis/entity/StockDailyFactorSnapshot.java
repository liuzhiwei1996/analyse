package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stock_daily_factor_snapshot")
public class StockDailyFactorSnapshot {
    @TableField("stock_code")
    private String stockCode;
    @TableField("trade_date")
    private LocalDate tradeDate;

    @TableField("close_previous")
    private BigDecimal closePrevious;
    @TableField("return_1d")
    private BigDecimal return1d;
    @TableField("return_3d")
    private BigDecimal return3d;
    @TableField("return_5d")
    private BigDecimal return5d;
    @TableField("return_10d")
    private BigDecimal return10d;
    @TableField("return_20d")
    private BigDecimal return20d;

    @TableField("volatility_5d")
    private BigDecimal volatility5d;
    @TableField("volatility_20d")
    private BigDecimal volatility20d;

    @TableField("avg_amount_5d")
    private BigDecimal avgAmount5d;
    @TableField("avg_amount_20d")
    private BigDecimal avgAmount20d;
    @TableField("avg_amount_60d")
    private BigDecimal avgAmount60d;

    @TableField("daily_high_20d")
    private BigDecimal dailyHigh20d;
    @TableField("daily_low_20d")
    private BigDecimal dailyLow20d;
    @TableField("position_20d")
    private BigDecimal position20d;

    @TableField("is_limit_up_near")
    private Boolean isLimitUpNear;
    @TableField("is_limit_down_near")
    private Boolean isLimitDownNear;

    @TableField("valid_flag")
    private Boolean validFlag;
    @TableField("invalid_reason")
    private String invalidReason;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
