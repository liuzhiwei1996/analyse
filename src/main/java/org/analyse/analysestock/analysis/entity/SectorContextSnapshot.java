package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sector_context_snapshot")
public class SectorContextSnapshot {
    @TableField("trade_date")
    private LocalDate tradeDate;
    @TableField("sector")
    private String sector;

    @TableField("sector_breadth_1430")
    private BigDecimal sectorBreadth1430;
    @TableField("sector_return_1430")
    private BigDecimal sectorReturn1430;
    @TableField("sector_avg_tail_momentum")
    private BigDecimal sectorAvgTailMomentum;
    @TableField("sector_rank")
    private Integer sectorRank;
    @TableField("sector_score")
    private BigDecimal sectorScore;

    @TableField("valid_stock_count")
    private Integer validStockCount;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
