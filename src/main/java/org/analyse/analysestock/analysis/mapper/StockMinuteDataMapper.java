package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@DS("analysis")
public interface StockMinuteDataMapper extends BaseMapper<StockMinuteData> {

    int insertBatch(@Param("list") List<StockMinuteData> list);

    List<LocalDate> findAllTradeDates();

    /**
     * 查询指定日期范围内有分时数据的股票代码列表
     */
    List<String> findStockCodesWithMinuteData(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * 查询指定日期范围内某只股票有分时数据的日期列表
     */
    List<LocalDate> findTradeDatesWithMinuteData(@Param("stockCode") String stockCode, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
}
