package org.analyse.analysestock.analysis.api;

import org.analyse.analysestock.config.ResultData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * <p>
 * VIEW Mapper 接口
 * </p>
 *
 * @author kennan
 * @since 2022-03-14
 */
public interface AnalysisApi {

    @GetMapping("/getAnalysis")
    ResultData<String> getAnalysis(String stockCode);
}
