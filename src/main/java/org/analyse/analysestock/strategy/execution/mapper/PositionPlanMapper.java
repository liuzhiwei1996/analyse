package org.analyse.analysestock.strategy.execution.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.strategy.execution.entity.PositionPlan;

@DS("analysis")
public interface PositionPlanMapper extends BaseMapper<PositionPlan> {
}
