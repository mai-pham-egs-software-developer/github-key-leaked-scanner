package com.leakscanner.detect;

/**
 * A single flagged, already-redacted candidate — the full key value is never retained.
 * {@code context} is the surrounding diff line with the matched span itself replaced by
 * {@code redacted}, so reviewers get placement/formatting without the real key.
 */
public record KeyMatch(
    Confidence confidence, boolean hasPrefix, String redacted, String raw, String filePath, String context) {

  public enum Confidence {
    LOW,
    MEDIUM,
    HIGH
  }
}
