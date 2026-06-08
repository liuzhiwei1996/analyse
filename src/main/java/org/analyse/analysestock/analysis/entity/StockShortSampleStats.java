package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stock_short_sample_stats")
public class StockShortSampleStats {
    @TableField("stock_code")
    private String stockCode;
    @TableField("trade_date")
    private LocalDate tradeDate;

    @TableField("sample_count")
    private Integer sampleCount;
    @TableField("win_count")
    private Integer winCount;
    @TableField("short_win_rate")
    private BigDecimal shortWinRate;

    @TableField("avg_gross_return_bps")
    private BigDecimal avgGrossReturnBps;
    @TableField("avg_cost_bps")
    private BigDecimal avgCostBps;
    @TableField("avg_net_return_bps")
    private BigDecimal avgNetReturnBps;

    @TableField("avg_win_bps")
    private BigDecimal avgWinBps;
    @TableField("avg_loss_bps")
    private BigDecimal avgLossBps;
    @TableField("profit_loss_ratio")
    private BigDecimal profitLossRatio;

    @TableField("max_loss_bps")
    private BigDecimal maxLossBps;
    @TableField("confidence_level")
    private BigDecimal confidenceLevel;

    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
