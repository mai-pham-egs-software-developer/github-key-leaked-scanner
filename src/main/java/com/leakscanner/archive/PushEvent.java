package com.leakscanner.archive;

/** A minimal projection of a GH Archive PushEvent — GitHub dropped the per-commit
 *  list from this payload a while back, so only the before/head range remains. */
public record PushEvent(String repoName, String before, String head, String createdAt) {}
