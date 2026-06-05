package org.analyse.analysestock.realtimecandidate.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class SecurityInfo {
    private String symbol;
    private String shortName;
    private LocalDate listingDate;
    private String stStatus;
    private String listedStatus;
    private String sector;
    private String markets;
}
