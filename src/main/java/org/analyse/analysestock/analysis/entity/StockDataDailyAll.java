package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 个股数据--每日
 */
@Data
@TableName("stock_data_daily_all")
public class StockDataDailyAll  extends AbstractStockDataDaily{

    private static final long serialVersionUID = 1L;


    @TableId(type = IdType.AUTO)
    private Long id;


    /**
     * 股票代码
     */
    private String stockCode;

    /**
     * 交易日期
     */
    private LocalDate tradeDate;

    /**
     * 开盘价
     */
    private BigDecimal open = BigDecimal.ZERO;

    /**
     * 最高价
     */
    private BigDecimal highest = BigDecimal.ZERO;

    /**
     * 最低价
     */
    private BigDecimal lowest = BigDecimal.ZERO;

    /**
     * 收盘价
     */
    private BigDecimal close = BigDecimal.ZERO;

    /**
     * 前复权收盘价
     */
    private BigDecimal closeForead = BigDecimal.ZERO;

    /**
     * 后复权收盘价
     */
    private BigDecimal closeBackad = BigDecimal.ZERO;

    /**
     * 成交额
     */
    private BigDecimal amount = BigDecimal.ZERO;

    /**
     * 昨收价
     */
    private BigDecimal closePrevious = BigDecimal.ZERO;

    /**
     * 成交量
     */
    private Long volume = 0L;


    /**
     * 是否删除：0否1是
     */
    private Integer isDeleted;

    /**
     * 删除备注
     */
    private String isDeletedDesc;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 创建人id
     */
    private String creatorId;

    /**
     * 更新时间
     */
    private LocalDateTime modifyTime;

    /**
     * 更新人id
     */
    private String modifierId;

    /**
     * 备注
     */
    private String remark;
}
