package com.dams.estimate.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** Quote a job before work starts. Lines carry free-text descriptions + amounts. */
@Getter
@Setter
@NoArgsConstructor
public class CreateEstimateRequest {

    @NotNull(message = "jobCardId is required")
    private Long jobCardId;

    @NotEmpty(message = "At least one estimate line is required")
    @Valid
    private List<Line> lines;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Line {

        @NotNull(message = "description is required")
        private String description;

        @NotNull(message = "amount is required")
        private BigDecimal amount;
    }
}
