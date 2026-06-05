package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@DS("analysis")
public interface StockDataDailyAllMapper extends BaseMapper<StockDataDailyAll> {

    /**
     * 根据股票代码和披露日期查询最新交易日的手数据
     * @param stockCode
     * @param holdingDate
     * @return
     */
    StockDataDailyAll findByStockCodeAndLtTradeDate(@Param("stockCode") String stockCode, @Param("holdingDate") LocalDate holdingDate);

    /**
     * 批量插入
     *
     * @param stockDataDailies
     * @return
     */
    int bulkInsert(@Param("stockDataDailies") List<StockDataDailyAll> stockDataDailies);

    /**
     * 根据股票代码和交易日期删除数据
     * @param stockCode
     * @param tradeDate
     * @return
     */
    int deleteByStockCodeAndTradeDate(@Param("stockCode") String stockCode, @Param("tradeDate") LocalDate tradeDate);
}
