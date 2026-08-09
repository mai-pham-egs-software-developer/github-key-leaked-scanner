package com.leakscanner.web;

import com.leakscanner.detect.KeyMatch;
import com.leakscanner.scan.ScanService;
import com.leakscanner.store.ScanResultStore;
import com.leakscanner.store.ScanStats;
import com.leakscanner.store.StoredMatch;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ScanController {

  private final ScanService scanService;
  private final ScanResultStore store;

  public ScanController(ScanService scanService, ScanResultStore store) {
    this.scanService = scanService;
    this.store = store;
  }

  /** Scans one UTC hour synchronously. date=YYYY-MM-DD, hour=0-23, sample=fraction (0,1]. */
  @PostMapping("/api/scans")
  public ScanStats scanHour(
      @RequestParam String date,
      @RequestParam int hour,
      @RequestParam(required = false) Double sample)
      throws IOException, InterruptedException {
    return scanService.scanHour(date, hour, sample, null);
  }

  /** Scans an inclusive UTC hour range on one date, one hour at a time, synchronously. */
  @PostMapping("/api/scans/range")
  public List<ScanStats> scanRange(
      @RequestParam String date,
      @RequestParam int fromHour,
      @RequestParam int toHour,
      @RequestParam(required = false) Double sample)
      throws IOException, InterruptedException {
    List<ScanStats> results = new java.util.ArrayList<>();
    for (int h = fromHour; h <= toHour; h++) {
      results.add(scanService.scanHour(date, h, sample, null));
    }
    return results;
  }

  /** Lists stored (redacted) matches — full key values are never stored, only a redacted snippet. */
  @GetMapping("/api/matches")
  public List<StoredMatch> matches(
      @RequestParam(required = false) String hour,
      @RequestParam(defaultValue = "low") String minConfidence) {
    KeyMatch.Confidence threshold = KeyMatch.Confidence.valueOf(minConfidence.toUpperCase(Locale.ROOT));
    return store.getMatches(hour).stream()
        .filter(m -> m.confidence().ordinal() >= threshold.ordinal())
        .toList();
  }
}
