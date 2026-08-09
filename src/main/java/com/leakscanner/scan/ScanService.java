package com.leakscanner.scan;

import com.leakscanner.archive.GhArchiveClient;
import com.leakscanner.config.ScannerProperties;
import com.leakscanner.detect.KeyMatch;
import com.leakscanner.detect.PrivateKeyDetector;
import com.leakscanner.patch.PatchFetcher;
import com.leakscanner.store.ScanResultStore;
import com.leakscanner.store.ScanStats;
import com.leakscanner.store.StoredMatch;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * Scans one UTC hour of GH Archive PushEvents for leaked private-key-shaped strings.
 *
 * GH Archive's PushEvent payload only carries {before, head, ref} — no per-commit list —
 * so each sampled push is diffed in one shot via GitHub's compare/.patch endpoint
 * (before..head). Only redacted findings are ever produced or persisted; see
 * {@link PrivateKeyDetector} and {@link ScanResultStore}.
 */
@Service
public class ScanService {

  private final GhArchiveClient archiveClient;
  private final PatchFetcher patchFetcher;
  private final PrivateKeyDetector detector;
  private final ScanResultStore store;
  private final ScannerProperties props;

  public ScanService(
      GhArchiveClient archiveClient,
      PatchFetcher patchFetcher,
      PrivateKeyDetector detector,
      ScanResultStore store,
      ScannerProperties props) {
    this.archiveClient = archiveClient;
    this.patchFetcher = patchFetcher;
    this.detector = detector;
    this.store = store;
    this.props = props;
  }

  public ScanStats scanHour(String date, int hour, Double sampleRateOverride, ProgressListener onProgress)
      throws IOException, InterruptedException {
    double sampleRate = sampleRateOverride != null ? sampleRateOverride : props.getSampleRate();
    String hourKey = archiveClient.hourKey(date, hour);

    AtomicLong pushEventCount = new AtomicLong();
    AtomicLong pushEventsSampled = new AtomicLong();
    AtomicLong patchesFetched = new AtomicLong();
    AtomicLong fetchErrors = new AtomicLong();
    List<StoredMatch> matches = new CopyOnWriteArrayList<>();
    List<CompletableFuture<Void>> pending = new ArrayList<>();
    Random random = new Random();

    archiveClient.forEachPushEvent(
        date,
        hour,
        evt -> {
          pushEventCount.incrementAndGet();
          if (random.nextDouble() > sampleRate) return;
          pushEventsSampled.incrementAndGet();

          CompletableFuture<Void> task =
              patchFetcher
                  .fetchPatch(evt.repoName(), evt.before(), evt.head())
                  .thenAccept(
                      patchText -> {
                        patchesFetched.incrementAndGet();
                        for (KeyMatch f : detector.scanPatch(patchText)) {
                          matches.add(
                              new StoredMatch(
                                  hourKey,
                                  evt.repoName(),
                                  evt.head(),
                                  f.filePath(),
                                  f.confidence(),
                                  f.hasPrefix(),
                                  f.redacted(),
                                  f.context(),
                                  evt.createdAt() != null ? evt.createdAt() : Instant.now().toString()));
                        }
                      })
                  .exceptionally(
                      ex -> {
                        fetchErrors.incrementAndGet();
                        return null;
                      })
                  .thenRun(
                      () -> {
                        if (onProgress != null) {
                          onProgress.onProgress(
                              pushEventCount.get(),
                              pushEventsSampled.get(),
                              patchesFetched.get(),
                              fetchErrors.get());
                        }
                      });
          pending.add(task);
        });

    CompletableFuture.allOf(pending.toArray(new CompletableFuture[0])).join();

    long high = countByConfidence(matches, KeyMatch.Confidence.HIGH);
    long medium = countByConfidence(matches, KeyMatch.Confidence.MEDIUM);
    long low = countByConfidence(matches, KeyMatch.Confidence.LOW);

    ScanStats stats =
        new ScanStats(
            hourKey,
            pushEventCount.get(),
            pushEventCount.get(),
            pushEventsSampled.get(),
            patchesFetched.get(),
            fetchErrors.get(),
            high,
            medium,
            low,
            Instant.now().toString());

    store.saveScanResult(stats, matches);
    return stats;
  }

  private static long countByConfidence(List<StoredMatch> matches, KeyMatch.Confidence confidence) {
    return matches.stream().filter(m -> m.confidence() == confidence).count();
  }
}
