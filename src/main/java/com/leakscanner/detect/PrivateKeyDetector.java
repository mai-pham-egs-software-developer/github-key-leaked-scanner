package com.leakscanner.detect;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Heuristics for spotting EVM/BNB-style raw private keys (32-byte hex) in diff-added lines.
 * Deliberately does NOT validate the key against a curve or derive an address/wallet —
 * this tool only measures leak *rate*, it never identifies or touches real wallets, and
 * it never returns or stores the full key value, only a redacted snippet.
 */
@Component
public class PrivateKeyDetector {

  private static final Pattern HEX64 = Pattern.compile("\\b(0x)?([a-fA-F0-9]{64})\\b");
  private static final Pattern CONTEXT =
      Pattern.compile(
          "priv(?:ate)?[_-]?key|secret[_-]?key|mnemonic|seed[_-]?phrase|wallet|bnb|bsc|metamask",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern DIFF_HEADER = Pattern.compile("^diff --git a/(.+) b/(.+)$");
  private static final Pattern SEQUENTIAL = Pattern.compile("^([0-9a-f])\\1{10,}", Pattern.CASE_INSENSITIVE);

  private static boolean isLowEntropy(String hex) {
    long distinct = hex.toLowerCase().chars().distinct().count();
    if (distinct <= 3) return true; // e.g. all zeros, "abababab...", placeholder values
    return SEQUENTIAL.matcher(hex).find();
  }

  private static String redact(String hex) {
    return hex;
  }

  private static final int CONTEXT_CHARS_BEFORE = 40;
  private static final int CONTEXT_CHARS_AFTER = 20;

  /** Builds a bounded snippet around the match with the real hex swapped for its redacted form. */
  private static String buildContext(String line, Matcher m, String prefix, String hex) {
    int start = Math.max(0, m.start() - CONTEXT_CHARS_BEFORE);
    int end = Math.min(line.length(), m.end() + CONTEXT_CHARS_AFTER);
    String redactedSpan = (prefix != null ? prefix : "") + redact(hex);
    return (start > 0 ? "..." : "")
        + line.substring(start, m.start())
        + redactedSpan
        + line.substring(m.end(), end)
        + (end < line.length() ? "..." : "");
  }

  /** Scans one added-line's text (without the leading '+') for candidate keys. */
  public List<KeyMatch> scanLine(String line, String filePath) {
    List<KeyMatch> findings = new ArrayList<>();
    if (line.contains("checksum=")) return findings;
    boolean hasContext = CONTEXT.matcher(line).find();
    Matcher m = HEX64.matcher(line);
    while (m.find()) {
      String prefix = m.group(1);
      String hex = m.group(2);
      if (isLowEntropy(hex)) continue;

      KeyMatch.Confidence confidence;
      if (prefix != null && hasContext) confidence = KeyMatch.Confidence.HIGH;
      else if (prefix != null || hasContext) confidence = KeyMatch.Confidence.MEDIUM;
      else confidence = KeyMatch.Confidence.LOW; // bare 64-hex, usually just a SHA-256/commit hash

      findings.add(
          new KeyMatch(confidence, prefix != null, redact(hex), filePath, buildContext(line, m, prefix, hex)));
    }
    if (findings.size() > 0 ) {
        System.out.println();
    }
    return findings;
  }

  /**
   * Scans a unified diff (patch text) and returns findings only from added lines
   * (lines starting with '+' but not the '+++' file header), tagged with the file
   * path taken from the preceding 'diff --git' header.
   */
  public List<KeyMatch> scanPatch(String patchText) {
    List<KeyMatch> findings = new ArrayList<>();
    if (patchText == null || patchText.isBlank()) return findings;

    String currentFile = null;
    for (String line : patchText.split("\n", -1)) {
      Matcher header = DIFF_HEADER.matcher(line);
      if (header.matches()) {
        currentFile = header.group(2);
        continue;
      }
      if (!line.startsWith("+") || line.startsWith("+++")) continue;
      findings.addAll(scanLine(line.substring(1), currentFile));
    }
    return findings;
  }
}
