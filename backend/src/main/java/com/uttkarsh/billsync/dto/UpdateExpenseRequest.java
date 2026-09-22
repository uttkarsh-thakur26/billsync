package com.uttkarsh.billsync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Only the description can change in place. Anything that moves money is deleted and re-added. */
public record UpdateExpenseRequest(@NotBlank @Size(max = 255) String description) {
}
