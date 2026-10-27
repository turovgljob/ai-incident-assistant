package com.example.incidents.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalyzeRequest(@NotBlank @Size(max = 8000) String description) {
}
