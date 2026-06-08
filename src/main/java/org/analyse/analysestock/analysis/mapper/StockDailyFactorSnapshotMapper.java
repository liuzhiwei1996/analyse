package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.StockDailyFactorSnapshot;

@DS("analysis")
public interface StockDailyFactorSnapshotMapper extends BaseMapper<StockDailyFactorSnapshot> {
    int insertBatch(@org.apache.ibatis.annotations.Param("list") java.util.List<StockDailyFactorSnapshot> list);

    @org.apache.ibatis.annotations.Delete("DELETE FROM stock_daily_factor_snapshot WHERE trade_date = #{tradeDate}")
    int deleteByTradeDate(@org.apache.ibatis.annotations.Param("tradeDate") java.time.LocalDate tradeDate);
}
