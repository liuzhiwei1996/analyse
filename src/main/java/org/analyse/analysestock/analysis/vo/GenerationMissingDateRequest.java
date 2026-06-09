package org.analyse.analysestock.analysis.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class GenerationMissingDateRequest {

    private LocalDate startDate;

    private LocalDate endDate;
}
