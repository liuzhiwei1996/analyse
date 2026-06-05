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
}
