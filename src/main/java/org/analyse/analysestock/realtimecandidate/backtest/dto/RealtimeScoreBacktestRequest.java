package org.analyse.analysestock.realtimecandidate.backtest.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
/**
 * 实时候选股评分回测请求参数。
 *
 * <p>该请求只影响回测区间、过滤条件和成本场景，不触发评分重新计算。</p>
 */
public class RealtimeScoreBacktestRequest {

    /**
     * 回测开始日期。
     */
    private LocalDate startDate;

    /**
     * 回测结束日期。
     */
    private LocalDate endDate;

    /**
     * 每日选股分组，例如 [5, 10, 20]。
     */
    private List<Integer> topKList;

    /**
     * 单次完整买卖固定成本，单位 bps。
     */
    private BigDecimal costBps;

    /**
     * 滑点成本，单位 bps。
     */
    private BigDecimal slippageBps;

    /**
     * 成本敏感性场景，单位 bps；为空时默认 [0, 10, costBps + slippageBps, 50]。
     */
    private List<BigDecimal> costScenarioBpsList;

    /**
     * 评分策略版本。
     */
    private String strategyVersion;

    /**
     * 最低评分过滤条件；为空则不过滤。
     */
    private BigDecimal minScore;

    /**
     * 最低置信度过滤条件；为空则不过滤。
     */
    private String minConfidenceLevel;

    /**
     * 是否排除无效评分记录；默认 true。
     */
    private Boolean excludeInvalid;
}
