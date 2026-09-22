package com.uttkarsh.billsync.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** Both fields optional; whatever is present is changed. */
public record UpdateUserRequest(
        @Size(max = 100) String name,
        @Email @Size(max = 255) String email) {
}
