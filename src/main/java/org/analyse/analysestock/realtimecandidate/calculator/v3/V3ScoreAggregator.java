package org.analyse.analysestock.realtimecandidate.calculator.v3;

import org.analyse.analysestock.realtimecandidate.dto.ScoreExplanation;
import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;
import org.analyse.analysestock.realtimecandidate.enums.ConfidenceLevel;
import org.analyse.analysestock.realtimecandidate.util.PercentileUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * V3 评分聚合器。
 *
 * <p>组合 7 大模块评分，按权重计算最终评分（百分制 0-100），并进行全市场排名。</p>
 *
 * <p>权重（需求文档第 5 节）：</p>
 * <ul>
 *   <li>25% 预期净收益评分</li>
 *   <li>20% 次日早盘冲高能力评分</li>
 *   <li>15% 买入成交适配度评分</li>
 *   <li>15% 尾盘强度评分</li>
 *   <li>10% 板块/市场环境评分</li>
 *   <li>10% 风险控制评分</li>
 *   <li>5% 短样本稳定性评分</li>
 * </ul>
 */
public class V3ScoreAggregator {

    // 权重常量
    private static final BigDecimal W_EXPECTED_NET = new BigDecimal("0.25");
    private static final BigDecimal W_MORNING_BREAKOUT = new BigDecimal("0.20");
    private static final BigDecimal W_BUY_EXECUTION = new BigDecimal("0.15");
    private static final BigDecimal W_TAIL_STRENGTH = new BigDecimal("0.15");
    private static final BigDecimal W_SECTOR_MARKET = new BigDecimal("0.10");
    private static final BigDecimal W_RISK_CONTROL = new BigDecimal("0.10");
    private static final BigDecimal W_SHORT_SAMPLE = new BigDecimal("0.05");

    /**
     * 计算单只股票的最终评分。
     *
     * @param snapshot      因子快照（各分项评分已填充）
     * @param marketDiscount 弱市场折扣系数
     * @return 0-100 百分制评分
     */
    public BigDecimal calculateFinalScore(V3FactorSnapshot snapshot, BigDecimal marketDiscount) {
        BigDecimal score = BigDecimal.ZERO;

        // 25% 预期净收益评分
        if (snapshot.getExpectedNetReturnScore() != null) {
            score = score.add(snapshot.getExpectedNetReturnScore().multiply(W_EXPECTED_NET));
        }

        // 20% 次日早盘冲高能力评分
        if (snapshot.getMorningBreakoutScore() != null) {
            score = score.add(snapshot.getMorningBreakoutScore().multiply(W_MORNING_BREAKOUT));
        }

        // 15% 买入成交适配度评分
        if (snapshot.getBuyExecutionFitScore() != null) {
            score = score.add(snapshot.getBuyExecutionFitScore().multiply(W_BUY_EXECUTION));
        }

        // 15% 尾盘强度评分
        if (snapshot.getTailStrengthScore() != null) {
            score = score.add(snapshot.getTailStrengthScore().multiply(W_TAIL_STRENGTH));
        }

        // 10% 板块/市场环境评分
        if (snapshot.getSectorMarketScore() != null) {
            score = score.add(snapshot.getSectorMarketScore().multiply(W_SECTOR_MARKET));
        }

        // 10% 风险控制评分
        if (snapshot.getRiskControlScore() != null) {
            score = score.add(snapshot.getRiskControlScore().multiply(W_RISK_CONTROL));
        }

        // 5% 短样本稳定性评分
        if (snapshot.getShortSampleScore() != null) {
            score = score.add(snapshot.getShortSampleScore().multiply(W_SHORT_SAMPLE));
        }

        // 应用过滤降权系数
        if (snapshot.getFilterDowngradeMultiplier() != null) {
            score = score.multiply(snapshot.getFilterDowngradeMultiplier());
        }

        // 应用弱市场折扣
        if (marketDiscount != null && marketDiscount.compareTo(BigDecimal.ONE) < 0) {
            score = score.multiply(marketDiscount);
        }

        // 转换为百分制
        return score.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算短样本稳定性评分（权重 5%）。
     *
     * <p>使用贝叶斯修正胜率，样本越少越向 50% 回归。</p>
     *
     * <p>置信度规则（需求文档第 19 节）：</p>
     */
    public BigDecimal calculateShortSampleScore(V3FactorSnapshot snapshot) {
        int sampleCount = snapshot.getShortSampleCount() != null ? snapshot.getShortSampleCount() : 0;

        // 样本 < 5：固定中性
        if (sampleCount < 5) {
            return new BigDecimal("0.5");
        }

        BigDecimal adjustedWinRate = snapshot.getShortAdjustedWinRate() != null
                ? snapshot.getShortAdjustedWinRate() : new BigDecimal("0.5");

        // 归一化平均净收益（50bps 为满分）
        BigDecimal normReturn = BigDecimal.ZERO;
        if (snapshot.getShortAvgNetReturnBps() != null) {
            normReturn = snapshot.getShortAvgNetReturnBps()
                    .divide(new BigDecimal("50"), 4, RoundingMode.HALF_UP)
                    .add(new BigDecimal("0.5"));
            if (normReturn.compareTo(BigDecimal.ONE) > 0) normReturn = BigDecimal.ONE;
            if (normReturn.compareTo(BigDecimal.ZERO) < 0) normReturn = BigDecimal.ZERO;
        } else {
            normReturn = new BigDecimal("0.5");
        }

        // 盈亏比调整
        BigDecimal plRatioScore = new BigDecimal("0.5");
        if (snapshot.getShortProfitLossRatio() != null) {
            BigDecimal plr = snapshot.getShortProfitLossRatio();
            if (plr.compareTo(new BigDecimal("2.0")) > 0) plRatioScore = BigDecimal.ONE;
            else if (plr.compareTo(new BigDecimal("1.5")) > 0) plRatioScore = new BigDecimal("0.8");
            else if (plr.compareTo(new BigDecimal("1.0")) > 0) plRatioScore = new BigDecimal("0.6");
            else plRatioScore = new BigDecimal("0.3");
        }

        // 综合：40% 修正胜率 + 30% 平均净收益 + 20% 盈亏比 + 10% 样本数量置信度
        BigDecimal sampleConfidence;
        if (sampleCount >= 60) sampleConfidence = BigDecimal.ONE;
        else if (sampleCount >= 20) sampleConfidence = new BigDecimal("0.8");
        else if (sampleCount >= 10) sampleConfidence = new BigDecimal("0.6");
        else sampleConfidence = new BigDecimal("0.4");

        BigDecimal score = adjustedWinRate.multiply(new BigDecimal("0.40"))
                .add(normReturn.multiply(new BigDecimal("0.30")))
                .add(plRatioScore.multiply(new BigDecimal("0.20")))
                .add(sampleConfidence.multiply(new BigDecimal("0.10")));

        // 限制最高分（样本少时）
        if (sampleCount < 10) {
            BigDecimal maxScore = new BigDecimal("0.7");
            if (score.compareTo(maxScore) > 0) score = maxScore;
        }

        return score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    /**
     * 全市场横截面标准化。
     *
     * <p>将各分项评分的原始值按全市场百分位排名转换为 0-1 范围的标准化分数。</p>
     */
    public void crossSectionalNormalize(List<V3FactorSnapshot> snapshots) {
        if (snapshots.isEmpty()) return;

        // expectedNetReturnScore 横截面标准化
        List<BigDecimal> enrScores = snapshots.stream()
                .map(V3FactorSnapshot::getExpectedNetReturnScore)
                .collect(Collectors.toList());
        List<BigDecimal> enrRanks = PercentileUtils.calculatePercentileRanks(enrScores);
        for (int i = 0; i < snapshots.size(); i++) {
            if (enrRanks.get(i) != null) {
                snapshots.get(i).setExpectedNetReturnScore(enrRanks.get(i));
            }
        }

        // morningBreakoutScore 横截面标准化
        List<BigDecimal> mbScores = snapshots.stream()
                .map(V3FactorSnapshot::getMorningBreakoutScore)
                .collect(Collectors.toList());
        List<BigDecimal> mbRanks = PercentileUtils.calculatePercentileRanks(mbScores);
        for (int i = 0; i < snapshots.size(); i++) {
            if (mbRanks.get(i) != null) {
                snapshots.get(i).setMorningBreakoutScore(mbRanks.get(i));
            }
        }

        // buyExecutionFitScore 横截面标准化
        List<BigDecimal> beScores = snapshots.stream()
                .map(V3FactorSnapshot::getBuyExecutionFitScore)
                .collect(Collectors.toList());
        List<BigDecimal> beRanks = PercentileUtils.calculatePercentileRanks(beScores);
        for (int i = 0; i < snapshots.size(); i++) {
            if (beRanks.get(i) != null) {
                snapshots.get(i).setBuyExecutionFitScore(beRanks.get(i));
            }
        }

        // tailStrengthScore 横截面标准化
        List<BigDecimal> tsScores = snapshots.stream()
                .map(V3FactorSnapshot::getTailStrengthScore)
                .collect(Collectors.toList());
        List<BigDecimal> tsRanks = PercentileUtils.calculatePercentileRanks(tsScores);
        for (int i = 0; i < snapshots.size(); i++) {
            if (tsRanks.get(i) != null) {
                snapshots.get(i).setTailStrengthScore(tsRanks.get(i));
            }
        }

        // sectorMarketScore 横截面标准化
        List<BigDecimal> smScores = snapshots.stream()
                .map(V3FactorSnapshot::getSectorMarketScore)
                .collect(Collectors.toList());
        List<BigDecimal> smRanks = PercentileUtils.calculatePercentileRanks(smScores);
        for (int i = 0; i < snapshots.size(); i++) {
            if (smRanks.get(i) != null) {
                snapshots.get(i).setSectorMarketScore(smRanks.get(i));
            }
        }

        // riskControlScore 横截面标准化
        List<BigDecimal> rcScores = snapshots.stream()
                .map(V3FactorSnapshot::getRiskControlScore)
                .collect(Collectors.toList());
        List<BigDecimal> rcRanks = PercentileUtils.calculatePercentileRanks(rcScores);
        for (int i = 0; i < snapshots.size(); i++) {
            if (rcRanks.get(i) != null) {
                snapshots.get(i).setRiskControlScore(rcRanks.get(i));
            }
        }

        // shortSampleScore 横截面标准化
        List<BigDecimal> ssScores = snapshots.stream()
                .map(V3FactorSnapshot::getShortSampleScore)
                .collect(Collectors.toList());
        List<BigDecimal> ssRanks = PercentileUtils.calculatePercentileRanks(ssScores);
        for (int i = 0; i < snapshots.size(); i++) {
            if (ssRanks.get(i) != null) {
                snapshots.get(i).setShortSampleScore(ssRanks.get(i));
            }
        }
    }

    /**
     * 生成评分解释。
     */
    public ScoreExplanation explain(V3FactorSnapshot snapshot) {
        ScoreExplanation explanation = new ScoreExplanation();

        // 加分原因
        if (snapshot.getMorningBreakoutScore() != null
                && snapshot.getMorningBreakoutScore().compareTo(new BigDecimal("0.7")) > 0) {
            explanation.getPositiveReasons().add("早盘冲高能力较强");
        }
        if (snapshot.getExpectedNetReturnScore() != null
                && snapshot.getExpectedNetReturnScore().compareTo(new BigDecimal("0.7")) > 0) {
            explanation.getPositiveReasons().add("预期净收益较高");
        }
        if (snapshot.getBuyExecutionFitScore() != null
                && snapshot.getBuyExecutionFitScore().compareTo(new BigDecimal("0.7")) > 0) {
            explanation.getPositiveReasons().add("买入成交适配度好");
        }
        if (snapshot.getTailStrengthScore() != null
                && snapshot.getTailStrengthScore().compareTo(new BigDecimal("0.7")) > 0) {
            explanation.getPositiveReasons().add("尾盘资金关注度强");
        }
        if (snapshot.getSectorMarketScore() != null
                && snapshot.getSectorMarketScore().compareTo(new BigDecimal("0.7")) > 0) {
            explanation.getPositiveReasons().add("板块/市场环境良好");
        }
        if (snapshot.getSectorRank() != null && snapshot.getSectorRank() <= 5) {
            explanation.getPositiveReasons().add("所属板块强度排名靠前（前5）");
        }
        if (snapshot.getBuy1PctAvgNetReturnBps() != null
                && snapshot.getBuy1PctAvgNetReturnBps().compareTo(new BigDecimal("15")) > 0) {
            explanation.getPositiveReasons().add("1%档买入历史收益好，轻微回踩有效");
        }

        // 扣分原因
        if (snapshot.getRiskControlScore() != null
                && snapshot.getRiskControlScore().compareTo(new BigDecimal("0.3")) < 0) {
            explanation.getNegativeReasons().add("风险控制评分偏低");
        }
        if (snapshot.getMaxDrop20d() != null
                && snapshot.getMaxDrop20d().compareTo(new BigDecimal("-0.07")) < 0) {
            explanation.getNegativeReasons().add("近20日最大单日跌幅过大");
        }
        if (snapshot.getAvgForceSellReturnBps() != null
                && snapshot.getAvgForceSellReturnBps().compareTo(new BigDecimal("-30")) < 0) {
            explanation.getNegativeReasons().add("强制卖出平均收益明显为负");
        }
        if (snapshot.getForceSellRate() != null
                && snapshot.getForceSellRate().compareTo(new BigDecimal("0.60")) > 0) {
            explanation.getNegativeReasons().add("强制卖出比例过高");
        }
        if (snapshot.getShortSampleCount() != null && snapshot.getShortSampleCount() < 10) {
            explanation.getNegativeReasons().add("样本数不足，置信度较低");
        }
        if (snapshot.getVolatility20d() != null
                && snapshot.getVolatility20d().compareTo(new BigDecimal("0.04")) > 0) {
            explanation.getNegativeReasons().add("近20日波动率偏高");
        }

        // 分项明细
        addScoreDetail(explanation, "预期净收益", snapshot.getExpectedNetReturnScore(), W_EXPECTED_NET);
        addScoreDetail(explanation, "早盘冲高", snapshot.getMorningBreakoutScore(), W_MORNING_BREAKOUT);
        addScoreDetail(explanation, "买入适配", snapshot.getBuyExecutionFitScore(), W_BUY_EXECUTION);
        addScoreDetail(explanation, "尾盘强度", snapshot.getTailStrengthScore(), W_TAIL_STRENGTH);
        addScoreDetail(explanation, "板块市场", snapshot.getSectorMarketScore(), W_SECTOR_MARKET);
        addScoreDetail(explanation, "风险控制", snapshot.getRiskControlScore(), W_RISK_CONTROL);
        addScoreDetail(explanation, "短样本", snapshot.getShortSampleScore(), W_SHORT_SAMPLE);

        return explanation;
    }

    /**
     * 计算置信度等级。
     */
    public String calculateConfidenceLevel(int sampleCount) {
        if (sampleCount >= 60) return ConfidenceLevel.HIGH_CONFIDENCE.name();
        if (sampleCount >= 20) return ConfidenceLevel.MEDIUM_CONFIDENCE.name();
        if (sampleCount >= 10) return ConfidenceLevel.MEDIUM_LOW_CONFIDENCE.name();
        if (sampleCount >= 5) return ConfidenceLevel.LOW_CONFIDENCE.name();
        return ConfidenceLevel.VERY_LOW_CONFIDENCE.name();
    }

    private void addScoreDetail(ScoreExplanation explanation, String name, BigDecimal score, BigDecimal weight) {
        ScoreExplanation.ScoreDetail detail = new ScoreExplanation.ScoreDetail();
        detail.setRawScore(score != null ? score : BigDecimal.ZERO);
        detail.setWeight(weight);
        detail.setWeightedScore(score != null ? score.multiply(weight) : BigDecimal.ZERO);
        explanation.getScoreDetails().put(name, detail);
    }
}
