package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.StockTailTradeSnapshotV3;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@DS("analysis")
public interface StockTailTradeSnapshotV3Mapper extends BaseMapper<StockTailTradeSnapshotV3> {

    int insertBatch(@Param("list") List<StockTailTradeSnapshotV3> list);

    @Delete("DELETE FROM stock_tail_trade_snapshot_v3 WHERE trade_date = #{tradeDate}")
    int deleteByTradeDate(@Param("tradeDate") LocalDate tradeDate);
}
