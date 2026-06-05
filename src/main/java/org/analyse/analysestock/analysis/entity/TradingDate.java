package org.analyse.analysestock.analysis.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 交易日期表
 * </p>
 *
 * @author dengzhiqiang
 * @since 2022-02-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class TradingDate implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 1：港交所；2：深交所；3：上交所；4：美...
     */
    private Integer market;

    /**
     * 交易所编号说明：
     * 1：港交所；2：深交所；3：上交所；4：美...
     */
    private String marketDesc;

    /**
     * 1: 全日市
     * 2: 半日市（假日等原因）
     * 3:  部分时段临时休市（台风等原因）
     */
    private Integer type;

    /**
     * 1: 全日市
     * 2: 半日市（假日等原因）
     * 3:  部分时段临时休市（台风等原因）
     */
    private String typeDesc;

    /**
     * 交易日期
     */
    private LocalDate tradingDate;

    /**
     * 是否删除：0否1是
     */
    private Integer isDeleted;

    /**
     * 删除描述
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
     * 备注说明
     */
    private String remark;


}
