package org.analyse.analysestock.strategy.filter.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.analyse.analysestock.strategy.filter.entity.CandidateFilterResult;

@DS("analysis")
public interface CandidateFilterResultMapper extends BaseMapper<CandidateFilterResult> {
}
