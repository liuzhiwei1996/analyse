package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.RealtimeCandidateScoreResult;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 实时候选股评分结果 Mapper。
 */
@DS("analysis")
public interface RealtimeCandidateScoreResultMapper extends BaseMapper<RealtimeCandidateScoreResult> {

    /**
     * 批量写入全市场评分结果。
     */
    int insertBatch(@Param("list") List<RealtimeCandidateScoreResult> list);

    /**
     * 删除指定交易日和策略版本的旧评分结果，保证全市场结果可重跑。
     */
    @Delete("DELETE FROM realtime_candidate_score_result WHERE trade_date = #{tradeDate} AND strategy_version = #{strategyVersion}")
    int deleteByTradeDateAndStrategyVersion(@Param("tradeDate") LocalDate tradeDate, @Param("strategyVersion") String strategyVersion);
}