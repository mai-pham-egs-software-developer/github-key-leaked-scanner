package com.leakscanner.store;

import com.leakscanner.config.ScannerProperties;
import com.leakscanner.detect.KeyMatch;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Persists scan results as one plain-text file per scanned hour, named
 * YYYY-MM-DD-HH.txt (zero-padded hour). Only redacted snippets are ever written —
 * see {@link com.leakscanner.detect.PrivateKeyDetector}.
 */
@Component
public class ScanResultStore {

  private static final Pattern KV = Pattern.compile("(\\w+)=(\"([^\"]*)\"|(\\S*))");

  private final Path scansDir;

  public ScanResultStore(ScannerProperties props) {
    this.scansDir = Path.of(props.getDataDir(), "scans");
  }

  private static String[] splitHourKey(String hourKey) {
    int idx = hourKey.lastIndexOf('-');
    return new String[] {hourKey.substring(0, idx), hourKey.substring(idx + 1)};
  }

  private Path fileForHourKey(String hourKey) {
    String[] parts = splitHourKey(hourKey);
    String date = parts[0];
    String hour = String.format(Locale.ROOT, "%02d", Integer.parseInt(parts[1]));
    return scansDir.resolve(date + "-" + hour + ".txt");
  }

  private static String quoteIfNeeded(String value) {
    if (value == null || value.isBlank()) return "\"\"";
    // the key=value line format has no escaping, so drop quotes rather than risk corrupting it
    String safe = value.replace('"', '\'');
    return safe.matches(".*\\s.*") ? "\"" + safe + "\"" : safe;
  }

  public void saveScanResult(ScanStats stats, List<StoredMatch> matches) {
    try {
      Files.createDirectories(scansDir);
      StringBuilder sb = new StringBuilder();
      sb.append("hour_key: ").append(stats.hourKey()).append('\n');
      sb.append("push_events: ").append(stats.pushEvents()).append('\n');
      sb.append("commits_seen: ").append(stats.commitsSeen()).append('\n');
      sb.append("commits_sampled: ").append(stats.commitsSampled()).append('\n');
      sb.append("patches_fetched: ").append(stats.patchesFetched()).append('\n');
      sb.append("fetch_errors: ").append(stats.fetchErrors()).append('\n');
      sb.append("matches_high: ").append(stats.matchesHigh()).append('\n');
      sb.append("matches_medium: ").append(stats.matchesMedium()).append('\n');
      sb.append("matches_low: ").append(stats.matchesLow()).append('\n');
      sb.append("scanned_at: ").append(stats.scannedAt()).append('\n');
      sb.append('\n');
      sb.append("matches:").append('\n');
      for (StoredMatch m : matches) {
        sb.append("confidence=").append(m.confidence().name().toLowerCase(Locale.ROOT)).append(' ');
        sb.append("repo=").append(quoteIfNeeded(m.repoName())).append(' ');
        sb.append("sha=").append(m.sha()).append(' ');
        sb.append("file=").append(quoteIfNeeded(m.filePath())).append(' ');
        sb.append("has_prefix=").append(m.hasPrefix() ? 1 : 0).append(' ');
        sb.append("redacted=").append(m.redacted()).append(' ');
        sb.append("context=").append(quoteIfNeeded(m.context())).append(' ');
        sb.append("found_at=").append(m.foundAt()).append('\n');
      }

      Files.writeString(fileForHourKey(stats.hourKey()), sb.toString(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private List<Path> listScanFiles() {
    if (!Files.isDirectory(scansDir)) return List.of();
    try (Stream<Path> paths = Files.list(scansDir)) {
      return paths
          .filter(p -> p.getFileName().toString().endsWith(".txt"))
          .sorted(Comparator.comparing(p -> p.getFileName().toString()))
          .collect(Collectors.toList());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private ParsedFile readScanFile(Path path) {
    try {
      String text = Files.readString(path, StandardCharsets.UTF_8);
      String[] parts = text.split("\nmatches:\n", 2);
      String header = parts[0];
      String body = parts.length > 1 ? parts[1] : "";

      String hourKey = null;
      long pushEvents = 0, commitsSeen = 0, commitsSampled = 0, patchesFetched = 0, fetchErrors = 0;
      long matchesHigh = 0, matchesMedium = 0, matchesLow = 0;
      String scannedAt = null;

      for (String line : header.split("\n")) {
        int idx = line.indexOf(": ");
        if (idx == -1) continue;
        String key = line.substring(0, idx);
        String value = line.substring(idx + 2);
        switch (key) {
          case "hour_key" -> hourKey = value;
          case "push_events" -> pushEvents = Long.parseLong(value);
          case "commits_seen" -> commitsSeen = Long.parseLong(value);
          case "commits_sampled" -> commitsSampled = Long.parseLong(value);
          case "patches_fetched" -> patchesFetched = Long.parseLong(value);
          case "fetch_errors" -> fetchErrors = Long.parseLong(value);
          case "matches_high" -> matchesHigh = Long.parseLong(value);
          case "matches_medium" -> matchesMedium = Long.parseLong(value);
          case "matches_low" -> matchesLow = Long.parseLong(value);
          case "scanned_at" -> scannedAt = value;
          default -> {}
        }
      }

      ScanStats stats =
          new ScanStats(
              hourKey, pushEvents, commitsSeen, commitsSampled, patchesFetched, fetchErrors,
              matchesHigh, matchesMedium, matchesLow, scannedAt);

      List<StoredMatch> matches = new ArrayList<>();
      for (String line : body.split("\n")) {
        if (line.isBlank()) continue;
        matches.add(parseMatchLine(line, hourKey));
      }
      return new ParsedFile(stats, matches);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static StoredMatch parseMatchLine(String line, String hourKey) {
    java.util.Map<String, String> kv = new java.util.HashMap<>();
    Matcher m = KV.matcher(line);
    while (m.find()) {
      String key = m.group(1);
      String value = m.group(3) != null ? m.group(3) : m.group(4);
      kv.put(key, value);
    }
    KeyMatch.Confidence confidence =
        KeyMatch.Confidence.valueOf(kv.getOrDefault("confidence", "low").toUpperCase(Locale.ROOT));
    String filePath = kv.get("file");
    return new StoredMatch(
        hourKey,
        kv.get("repo"),
        kv.get("sha"),
        (filePath == null || filePath.isBlank()) ? null : filePath,
        confidence,
        "1".equals(kv.get("has_prefix")),
        kv.get("redacted"),
        kv.get("context"),
        kv.get("found_at"));
  }

  public List<ScanStats> getSummary() {
    return listScanFiles().stream().map(p -> readScanFile(p).stats()).collect(Collectors.toList());
  }

  public List<StoredMatch> getMatches(String hourKey) {
    List<Path> files;
    if (hourKey != null) {
      Path f = fileForHourKey(hourKey);
      files = Files.exists(f) ? List.of(f) : List.of();
    } else {
      files = listScanFiles();
    }
    List<StoredMatch> out = new ArrayList<>();
    for (Path f : files) out.addAll(readScanFile(f).matches());
    return out;
  }

  private record ParsedFile(ScanStats stats, List<StoredMatch> matches) {}
}
