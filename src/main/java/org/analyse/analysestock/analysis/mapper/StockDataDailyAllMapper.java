package org.analyse.analysestock.analysis.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.apache.ibatis.annotations.Mapper;

@DS("analysis")
public interface StockDataDailyAllMapper extends BaseMapper<StockDataDailyAll> {
}
