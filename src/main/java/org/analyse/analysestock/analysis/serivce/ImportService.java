package org.analyse.analysestock.analysis.serivce;

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
}
