package org.analyse.analysestock.analysis.controller;

import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.api.AnalysisApi;
import org.analyse.analysestock.config.ResultData;
import org.analyse.analysestock.config.ResultUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: keenan
 * @Description:
 * @Date: create in 2026/4/23 11:11
 */
@Slf4j
@RestController
@RequestMapping("/anlysis")
public class AnalysisController implements AnalysisApi {

    @Override
    @GetMapping("/getAnalysis")
    public ResultData<String> getAnalysis(String stockCode) {
        return ResultUtil.success("test");
    }
}
