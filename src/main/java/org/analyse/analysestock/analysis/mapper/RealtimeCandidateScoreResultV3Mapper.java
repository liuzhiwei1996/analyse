package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.RealtimeCandidateScoreResultV3;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@DS("analysis")
public interface RealtimeCandidateScoreResultV3Mapper extends BaseMapper<RealtimeCandidateScoreResultV3> {

    int insertBatch(@Param("list") List<RealtimeCandidateScoreResultV3> list);

    @Delete("DELETE FROM realtime_candidate_score_result_v3 WHERE trade_date = #{tradeDate} AND strategy_version = #{strategyVersion}")
    int deleteByTradeDateAndStrategyVersion(@Param("tradeDate") LocalDate tradeDate, @Param("strategyVersion") String strategyVersion);
}
