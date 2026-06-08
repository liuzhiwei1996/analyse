package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("market_context_snapshot")
public class MarketContextSnapshot {
    @TableField("trade_date")
    private LocalDate tradeDate;

    @TableField("market_breadth_1430")
    private BigDecimal marketBreadth1430;
    @TableField("market_return_1430")
    private BigDecimal marketReturn1430;
    @TableField("market_avg_tail_momentum")
    private BigDecimal marketAvgTailMomentum;
    @TableField("market_avg_tail_volume_ratio")
    private BigDecimal marketAvgTailVolumeRatio;

    @TableField("valid_stock_count")
    private Integer validStockCount;
    @TableField("up_stock_count")
    private Integer upStockCount;
    @TableField("down_stock_count")
    private Integer downStockCount;

    @TableField("strong_market_flag")
    private Boolean strongMarketFlag;
    @TableField("weak_market_flag")
    private Boolean weakMarketFlag;

    @TableField("regime_score")
    private BigDecimal regimeScore;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
