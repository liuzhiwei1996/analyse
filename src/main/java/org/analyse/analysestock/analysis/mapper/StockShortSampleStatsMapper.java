package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.StockShortSampleStats;

@DS("analysis")
public interface StockShortSampleStatsMapper extends BaseMapper<StockShortSampleStats> {
    int insertBatch(@org.apache.ibatis.annotations.Param("list") java.util.List<StockShortSampleStats> list);

    @org.apache.ibatis.annotations.Delete("DELETE FROM stock_short_sample_stats WHERE trade_date = #{tradeDate}")
    int deleteByTradeDate(@org.apache.ibatis.annotations.Param("tradeDate") java.time.LocalDate tradeDate);
}
