package org.analyse.analysestock.analysis.serivce;

import org.analyse.analysestock.analysis.vo.GenerationMissingDateResponse;
import org.analyse.analysestock.analysis.vo.SnapshotTaskProgress;

import java.time.LocalDate;

/**
 * <p>
 * VIEW Mapper 接口
 * </p>
 *
 * @author kennan
 * @since 2022-03-14
 */
public interface ImportService {

    /**
     * 导入个股分时数据(rds接口只能拿到当日的数据)
     * @return
     */
    Integer importStockMinuteData(String stockCode, LocalDate tradeDate);

    /**
     * 导入个股日线数据
     * @return
     */
    Integer importStockDailyData(String stockCode, LocalDate tradeDate);

    /**
     * 计算实时候选股评分
     * @param stockCode 股票代码 (可选)
     * @param tradeDate 交易日期 (可选)
     * @return 评分记录
     */
    java.util.List<org.analyse.analysestock.realtimecandidate.dto.RealtimeCandidateScoreRecord> calculateRealtimeCandidateScores(String stockCode, LocalDate tradeDate);

    void prepareTailTradeSnapshot(LocalDate tradeDate);

    void prepareDailyFactorSnapshot(LocalDate tradeDate);

    void prepareMarketContextSnapshot(LocalDate tradeDate);

    void prepareShortSampleStats(LocalDate tradeDate);

    void prepareIntradayExecutionSnapshot(LocalDate tradeDate);

    GenerationMissingDateResponse findMissingGenerationDates(LocalDate startDate, LocalDate endDate);

    /**
     * 检测指定交易日范围内哪些股票缺失日K或分时数据
     * @param stockCode 股票代码（可选，为空则检查全市场）
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 缺失数据条目列表
     */
    java.util.List<org.analyse.analysestock.analysis.vo.MissingStockDataItem> checkMissingData(String stockCode, LocalDate startDate, LocalDate endDate);

    // ==================== V3 方法 ====================

    /**
     * 计算 V3 实时候选股评分。
     */
    java.util.List<org.analyse.analysestock.analysis.entity.RealtimeCandidateScoreResultV3> calculateRealtimeCandidateScoresV3(String stockCode, LocalDate tradeDate);

    java.util.List<org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot> listRealtimeCandidateFactorSnapshotsV3(String stockCode, LocalDate tradeDate);

    SnapshotTaskProgress startPrepareSnapshotsV3(LocalDate tradeDate);

    SnapshotTaskProgress getPrepareSnapshotsProgressV3(String taskId);

    /**
     * 准备 V3 增强尾盘快照。
     */
    void prepareTailTradeSnapshotV3(LocalDate tradeDate);

    /**
     * 准备 V3 短样本统计。
     */
    void prepareShortSampleStatsV3(LocalDate tradeDate);

    /**
     * 准备 V3 市场环境快照。
     */
    void prepareMarketContextSnapshotV3(LocalDate tradeDate);
}
