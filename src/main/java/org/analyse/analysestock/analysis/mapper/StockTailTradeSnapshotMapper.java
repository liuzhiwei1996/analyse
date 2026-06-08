package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.StockTailTradeSnapshot;

@DS("analysis")
public interface StockTailTradeSnapshotMapper extends BaseMapper<StockTailTradeSnapshot> {
    int insertBatch(@org.apache.ibatis.annotations.Param("list") java.util.List<StockTailTradeSnapshot> list);

    @org.apache.ibatis.annotations.Delete("DELETE FROM stock_tail_trade_snapshot WHERE trade_date = #{tradeDate}")
    int deleteByTradeDate(@org.apache.ibatis.annotations.Param("tradeDate") java.time.LocalDate tradeDate);
}
