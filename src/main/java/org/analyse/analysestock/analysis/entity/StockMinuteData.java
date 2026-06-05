package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票分钟成交数据表
 */
@Data
@TableName("stock_minute_data")
public class StockMinuteData {

    /**
     * 股票代码
     */
    private Integer stockCode;

    /**
     * 交易日
     */
    private LocalDate tradeDate;

    /**
     * 时间（分钟）
     */
    private Integer time;

    /**
     * 价格
     */
    private BigDecimal price;

    /**
     * 分钟成交量
     */
    private Long minuteVolume;

    /**
     * 分钟总额
     */
    private BigDecimal minuteAmount;

    /**
     * 最高价
     */
    private BigDecimal highPrice;

    /**
     * 最低价
     */
    private BigDecimal lowPrice;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime modifyTime;
}
