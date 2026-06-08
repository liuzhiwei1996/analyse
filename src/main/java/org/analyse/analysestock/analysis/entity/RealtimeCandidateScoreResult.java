package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("realtime_candidate_score_result")
/**
 * 实时候选股评分落库结果。
 *
 * <p>回测只读取这张表，避免在回测过程中重新计算评分导致引入未来数据或口径漂移。</p>
 */
public class RealtimeCandidateScoreResult {

    /**
     * 评分所属交易日，必须是全市场统一评分后的日期。
     */
    @TableField("trade_date")
    private LocalDate tradeDate;

    /**
     * 股票代码。
     */
    @TableField("stock_code")
    private String stockCode;

    /**
     * 股票简称，用于回测明细展示。
     */
    @TableField("short_name")
    private String shortName;

    /**
     * T 日 14:30 买入参考价。
     */
    @TableField("price_1430")
    private BigDecimal price1430;

    /**
     * 实时评分最终分，用于每日 TopK 排序。
     */
    @TableField("final_score")
    private BigDecimal finalScore;

    /**
     * 当日全市场排名；为空时回测按分数和股票代码兜底排序。
     */
    @TableField("rank_no")
    private Integer rankNo;

    /**
     * 评分置信度。
     */
    @TableField("confidence_level")
    private String confidenceLevel;

    /**
     * 评分记录是否有效；excludeInvalid=true 时只使用有效记录。
     */
    @TableField("valid_flag")
    private Boolean validFlag;

    /**
     * 策略版本，用于隔离不同评分模型的历史结果。
     */
    @TableField("strategy_version")
    private String strategyVersion;

    /**
     * 评分结果创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
