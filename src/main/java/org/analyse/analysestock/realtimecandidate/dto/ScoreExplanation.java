package org.analyse.analysestock.realtimecandidate.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V3 评分解释。
 *
 * <p>记录主要加分原因和扣分原因，方便排查为什么某只股票分高或分低。</p>
 */
@Data
public class ScoreExplanation {

    /**
     * 主要加分原因列表。
     */
    private List<String> positiveReasons = new ArrayList<>();

    /**
     * 主要扣分原因列表。
     */
    private List<String> negativeReasons = new ArrayList<>();

    /**
     * 各分项评分明细，key=分项名称, value=原始分和权重。
     */
    private Map<String, ScoreDetail> scoreDetails = new LinkedHashMap<>();

    /**
     * 分项评分明细。
     */
    @Data
    public static class ScoreDetail {
        private BigDecimal rawScore;
        private BigDecimal weight;
        private BigDecimal weightedScore;
    }
}
