package org.analyse.analysestock.strategy.market.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("market_regime_snapshot")
public class MarketRegimeSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    private String regime;

    private BigDecimal indexTrend;

    private BigDecimal breadthRatio;

    private BigDecimal turnoverRatio;

    private BigDecimal atr20Percentile;

    private BigDecimal tailStrength;

    private String reasoning;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
