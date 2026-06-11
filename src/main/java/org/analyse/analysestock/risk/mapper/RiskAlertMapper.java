package org.analyse.analysestock.risk.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.risk.entity.RiskAlert;

@DS("analysis")
public interface RiskAlertMapper extends BaseMapper<RiskAlert> {
}
