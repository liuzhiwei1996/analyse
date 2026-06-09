package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@DS("analysis")
public interface StockIntradayExecutionSnapshotMapper extends BaseMapper<StockIntradayExecutionSnapshot> {
    int insertBatch(@Param("list") List<StockIntradayExecutionSnapshot> list);

    @Delete("DELETE FROM stock_intraday_execution_snapshot WHERE trade_date = #{tradeDate}")
    int deleteByTradeDate(@Param("tradeDate") LocalDate tradeDate);
}
