package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 证券基本信息表
 */
@Data
@TableName("pub_stock_info")
public class PubStockInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 证券ID
     */
    private Integer securityId;

    /**
     * 上市公司ID
     */
    private Integer institutionId;

    /**
     * 公司中文全称
     */
    private String institutionName;

    /**
     * 股票代码
     */
    private String symbol;

    /**
     * 公司中文简称
     */
    private String shortName;

    /**
     * 拼音简称
     */
    private String pyShortName;

    /**
     * ISIN编码
     */
    private String isin;

    /**
     * 首次上市日期
     */
    private LocalDate listingDate;

    /**
     * 退市日期
     */
    private LocalDate delistDate;

    /**
     * 面值
     */
    private BigDecimal faceValue;

    /**
     * 发行价格
     */
    private BigDecimal issuePrice;

    /**
     * 注册资本货币
     */
    private String currency;

    /**
     * 上市市场
     */
    private String markets;

    /**
     * 上市板块
     */
    private String sector;

    /**
     * 股票类别
     */
    private String shareClasses;

    /**
     * 上市状态
     */
    private String listedStatus;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime modifyTime;

    /**
     * DataFrom
     */
    private String dataFrom;

    /**
     * 特别处理状态
     */
    private String stStatus;
}
