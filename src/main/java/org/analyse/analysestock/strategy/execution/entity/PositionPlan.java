package org.analyse.analysestock.strategy.execution.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("position_plan")
public class PositionPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    private String stockCode;

    private BigDecimal positionSize;

    private BigDecimal entryPrice;

    private BigDecimal stopLossPrice;

    private BigDecimal takeProfitPrice;

    private LocalDate timeStopDate;

    private BigDecimal riskBudgetPct;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
