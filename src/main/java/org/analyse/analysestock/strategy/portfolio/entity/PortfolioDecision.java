package org.analyse.analysestock.strategy.portfolio.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("portfolio_decision")
public class PortfolioDecision {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    private String stockCode;

    private BigDecimal rankValue;

    private BigDecimal pWin;

    private BigDecimal avgWinBps;

    private BigDecimal avgLossBps;

    private BigDecimal costBps;

    private BigDecimal riskUnit;

    private BigDecimal positionPct;

    private String regime;

    private String reasoning;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
