package org.analyse.analysestock.analysis.vo;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class GenerationMissingDateResponse {

    private LocalDate startDate;

    private LocalDate endDate;

    private Integer tradeDayCount;

    private Integer missingFactorCount;

    private Integer missingScoreCount;

    private List<LocalDate> tradeDates;

    private List<LocalDate> missingFactorDates;

    private List<LocalDate> missingScoreDates;

    private List<GenerationMissingDateItem> items;
}
