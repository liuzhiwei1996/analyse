package org.analyse.analysestock.strategy.market.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.strategy.market.entity.MarketRegimeSnapshot;

@DS("analysis")
public interface MarketRegimeSnapshotMapper extends BaseMapper<MarketRegimeSnapshot> {
}
