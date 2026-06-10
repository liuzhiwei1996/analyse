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
 * V3 板块环境快照。
 */
@Data
@TableName("sector_context_snapshot_v3")
public class SectorContextSnapshotV3 {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("trade_date")
    private LocalDate tradeDate;

    @TableField("sector_name")
    private String sectorName;

    // ==================== 板块指标 ====================
    @TableField("sector_strength_1430")
    private BigDecimal sectorStrength1430;

    @TableField("sector_breadth_1430")
    private BigDecimal sectorBreadth1430;

    @TableField("sector_volume_ratio")
    private BigDecimal sectorVolumeRatio;

    @TableField("sector_rank")
    private Integer sectorRank;

    @TableField("stock_count")
    private Integer stockCount;

    // ==================== 元数据 ====================
    @TableField("created_at")
    private LocalDateTime createdAt;
}
