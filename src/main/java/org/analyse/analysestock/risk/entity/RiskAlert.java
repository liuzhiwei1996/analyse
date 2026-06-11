package org.analyse.analysestock.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("risk_alert")
public class RiskAlert {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    private String alertType;

    private String severity;

    private String message;

    private String relatedStockCode;

    private Boolean resolved;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
