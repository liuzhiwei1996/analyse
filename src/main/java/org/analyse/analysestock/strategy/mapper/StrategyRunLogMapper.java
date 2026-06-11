package org.analyse.analysestock.strategy.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.strategy.entity.StrategyRunLog;

@DS("analysis")
public interface StrategyRunLogMapper extends BaseMapper<StrategyRunLog> {
}
