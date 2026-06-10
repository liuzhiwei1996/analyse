package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.MarketContextSnapshotV3;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@DS("analysis")
public interface MarketContextSnapshotV3Mapper extends BaseMapper<MarketContextSnapshotV3> {

    @Delete("DELETE FROM market_context_snapshot_v3 WHERE trade_date = #{tradeDate}")
    int deleteByTradeDate(@Param("tradeDate") LocalDate tradeDate);
}
