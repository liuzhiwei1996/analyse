package org.analyse.analysestock.realtimecandidate.backtest.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * V3 回测请求参数。
 *
 * <p>继承 V2 的 RealtimeScoreBacktestRequest，新增基线对照配置。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class V3BacktestRequest extends RealtimeScoreBacktestRequest {

    /**
     * 是否启用随机选股基线对照。
     */
    private Boolean enableRandomBaseline = false;

    /**
     * 随机迭代次数，默认 100 次。
     */
    private Integer randomIterations = 100;

    /**
     * 是否启用 BottomK 基线对照。
     */
    private Boolean enableBottomKBaseline = false;

    /**
     * 是否启用 MiddleK 基线对照。
     */
    private Boolean enableMiddleKBaseline = false;

    /**
     * MiddleK 采样数量。
     */
    private Integer middleKSampleSize = 100;

    /**
     * 是否启用每日明细输出。
     */
    private Boolean enableDailyDetailOutput = false;

    /**
     * 获取启用基线对照的 TopK 列表（含随机/BottomK/MiddleK 需要的规模）。
     */
    public List<Integer> getEffectiveTopKList() {
        List<Integer> topKList = getTopKList();
        if (topKList == null || topKList.isEmpty()) {
            topKList = new ArrayList<>();
            topKList.add(5);
            topKList.add(10);
            topKList.add(20);
        }
        return topKList;
    }
}
