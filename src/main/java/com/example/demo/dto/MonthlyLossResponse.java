package com.example.demo.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonthlyLossResponse {

    // Mes con formato AAAA-MM.
    private String month;
    private BigDecimal totalAmount;
    private long lossCount;
    private long unitsLost;
}
