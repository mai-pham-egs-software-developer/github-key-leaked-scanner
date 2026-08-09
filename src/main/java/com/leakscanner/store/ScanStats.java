package com.leakscanner.store;

public record ScanStats(
    String hourKey,
    long pushEvents,
    long commitsSeen,
    long commitsSampled,
    long patchesFetched,
    long fetchErrors,
    long matchesHigh,
    long matchesMedium,
    long matchesLow,
    String scannedAt) {}
