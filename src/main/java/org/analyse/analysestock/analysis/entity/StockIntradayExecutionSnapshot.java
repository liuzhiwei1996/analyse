package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("stock_intraday_execution_snapshot")
public class StockIntradayExecutionSnapshot {
    @TableField("stock_code")
    private String stockCode;
    @TableField("trade_date")
    private LocalDate tradeDate;

    @TableField("price_1430")
    private BigDecimal price1430;

    @TableField("low_1435_1444")
    private BigDecimal low14351444;
    @TableField("low_1445_1454")
    private BigDecimal low14451454;
    @TableField("low_1455_1500")
    private BigDecimal low14551500;

    @TableField("high_0930_0935")
    private BigDecimal high09300935;
    @TableField("high_0936_0940")
    private BigDecimal high09360940;
    @TableField("high_0941_0944")
    private BigDecimal high09410944;

    @TableField("price_0945")
    private BigDecimal price0945;

    @TableField("valid_flag")
    private Boolean validFlag;
    @TableField("invalid_reason")
    private String invalidReason;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
