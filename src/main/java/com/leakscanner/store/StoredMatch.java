package com.leakscanner.store;

import com.leakscanner.detect.KeyMatch;

/** A persisted, redacted-only finding — the full key value is never stored. */
public record StoredMatch(
    String hourKey,
    String repoName,
    String sha,
    String filePath,
    KeyMatch.Confidence confidence,
    boolean hasPrefix,
    String redacted,
    String context,
    String foundAt) {}
