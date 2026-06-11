package org.analyse.analysestock.strategy.portfolio.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.strategy.portfolio.entity.PortfolioDecision;

@DS("analysis")
public interface PortfolioDecisionMapper extends BaseMapper<PortfolioDecision> {
}
