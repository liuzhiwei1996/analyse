package org.analyse.analysestock.realtimecandidate.dto;

import lombok.Data;
import org.analyse.analysestock.realtimecandidate.enums.ConfidenceLevel;
import org.analyse.analysestock.realtimecandidate.enums.DataQualityFlag;
import org.analyse.analysestock.realtimecandidate.enums.InvalidReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RealtimeCandidateScoreRecord {
    /**
     * 交易日期
     */
    private LocalDate tradeDate;
    /**
     * 股票代码
     */
    private String stockCode;
    /**
     * 简称
     */
    private String shortName;
    /**
     * 市场
     */
    private String market;
    /**
     * 板块/行业
     */
    private String sector;

    /**
     * 14:00 价格
     */
    private BigDecimal price1400;
    /**
     * 14:30 价格
     */
    private BigDecimal price1430;
    /**
     * 14:30 买入参考价
     */
    private BigDecimal buyRefPrice1430;

    /**
     * 尾盘动量 (14:00-14:30 收益率)
     */
    private BigDecimal tailMomentum;
    /**
     * 14:00-14:30 成交额
     */
    private BigDecimal tailAmount1400To1430;
    /**
     * 尾盘成交量比 (相对于全天或均值)
     */
    private BigDecimal tailVolumeRatio;
    /**
     * 日内价格位置 (0-1)
     */
    private BigDecimal intradayPosition;

    /**
     * T日 14:30 收益率
     */
    private BigDecimal returnTo1430;
    /**
     * 近5日收益率
     */
    private BigDecimal return5d;
    /**
     * 近20日收益率
     */
    private BigDecimal return20d;
    /**
     * 近20日年化波动率
     */
    private BigDecimal volatility20d;
    /**
     * 近20日平均成交额
     */
    private BigDecimal avgAmount20d;

    /**
     * 市场宽度 (上涨家数占比)
     */
    private BigDecimal marketBreadth;
    /**
     * 市场 14:30 收益率 (大盘走势)
     */
    private BigDecimal marketReturn1430;
    /**
     * 板块相对强度
     */
    private BigDecimal sectorStrength;
    /**
     * 板块宽度
     */
    private BigDecimal sectorBreadth;
    /**
     * 个股相对强度
     */
    private BigDecimal relativeStrength;

    /**
     * 短样本统计样本数 (最近21日)
     */
    private int shortSampleCount;
    /**
     * 短样本胜率
     */
    private BigDecimal shortWinRate;
    /**
     * 短样本平均净收益 (Bps)
     */
    private BigDecimal shortAvgNetReturnBps;

    /**
     * 尾盘动量得分
     */
    private BigDecimal tailMomentumScore;
    /**
     * 量能强度得分
     */
    private BigDecimal tailVolumeScore;
    /**
     * 日内位置得分
     */
    private BigDecimal intradayPositionScore;
    /**
     * 日K趋势得分
     */
    private BigDecimal dailyTrendScore;
    /**
     * 波动率因子得分
     */
    private BigDecimal volatilityScore;
    /**
     * 市场环境得分
     */
    private BigDecimal regimeScore;
    /**
     * 短样本表现得分
     */
    private BigDecimal shortSampleScore;

    /**
     * 最终加权总分
     */
    private BigDecimal finalScore;
    /**
     * 排名
     */
    private int rankNo;

    /**
     * 置信度 (HIGH/MEDIUM/LOW)
     */
    private ConfidenceLevel confidenceLevel;
    /**
     * 策略版本
     */
    private String strategyVersion = "V1";
    /**
     * 是否有效
     */
    private boolean validFlag;
    /**
     * 无效原因
     */
    private InvalidReason invalidReason;
    /**
     * 数据质量标志
     */
    private DataQualityFlag dataQualityFlag;
    /**
     * 记录创建时间
     */
    private LocalDateTime createdAt = LocalDateTime.now();
}
