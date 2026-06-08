package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.MarketContextSnapshot;

@DS("analysis")
public interface MarketContextSnapshotMapper extends BaseMapper<MarketContextSnapshot> {
    @org.apache.ibatis.annotations.Delete("DELETE FROM market_context_snapshot WHERE trade_date = #{tradeDate}")
    int deleteByTradeDate(@org.apache.ibatis.annotations.Param("tradeDate") java.time.LocalDate tradeDate);
}
