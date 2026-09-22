package com.uttkarsh.billsync.dto;

import java.time.Instant;
import java.util.List;

/** The one error shape every endpoint returns. */
public record ApiError(Instant timestamp, int status, String error, List<String> details) {
}
