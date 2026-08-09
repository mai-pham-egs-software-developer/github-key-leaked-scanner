package com.leakscanner.patch;

import com.leakscanner.config.ScannerProperties;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches the unified diff for a push (before..head) via GitHub's unauthenticated
 * .patch/.diff endpoints on github.com — not the REST API, so it isn't subject to
 * api.github.com rate limits, but GitHub may still throttle abusive request rates,
 * hence the per-fetch delay and retry/backoff below.
 */
@Component
public class PatchFetcher {

  private static final Logger log = LoggerFactory.getLogger(PatchFetcher.class);
  private static final String ZERO_SHA = "0".repeat(40);

  private final ScannerProperties props;
  private final HttpClient http = HttpClient.newHttpClient();
  private final ExecutorService pool;

  public PatchFetcher(ScannerProperties props) {
    this.props = props;
    this.pool = Executors.newFixedThreadPool(Math.max(1, props.getConcurrency()));
  }

  private static String diffUrl(String repoName, String before, String head) {
    if (before == null || before.isBlank() || before.equals(ZERO_SHA)) {
      // new branch / first push on a ref — no prior commit to diff against, so just
      // look at the tip commit (earlier commits in the same push may be missed).
      return "https://github.com/" + repoName + "/commit/" + head + ".patch";
    }
    return "https://github.com/" + repoName + "/compare/" + before + "..." + head + ".patch";
  }

  /** Returns the patch text, or null if the commit/repo is gone (404/451). */
  public CompletableFuture<String> fetchPatch(String repoName, String before, String head) {
    return CompletableFuture.supplyAsync(() -> fetchWithRetry(repoName, before, head), pool);
  }

  private String fetchWithRetry(String repoName, String before, String head) {
    String url = diffUrl(repoName, before, head);
    Exception lastError = null;
    for (int attempt = 0; attempt <= props.getMaxRetries(); attempt++) {
      sleep(props.getRequestDelayMs());
      long startedAt = System.nanoTime();
      try {
        log.debug("GET {} (attempt {}/{})", url, attempt + 1, props.getMaxRetries() + 1);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
        log.debug("GET {} -> {} in {}ms", url, status, elapsedMs);

        if (status == 404 || status == 451) return null;
        if (status == 429 || status == 403) {
          long retryAfterSec = parseRetryAfter(response, attempt);
          log.warn("GET {} -> {}, backing off {}s (attempt {}/{})", url, status, retryAfterSec, attempt + 1, props.getMaxRetries() + 1);
          sleep(retryAfterSec * 1000);
          continue;
        }
        if (status < 200 || status >= 300) {
          log.warn("GET {} -> unexpected status {}", url, status);
          lastError = new RuntimeException("HTTP " + status + " for " + url);
          continue;
        }
        return response.body();
      } catch (Exception e) {
        log.warn("GET {} failed (attempt {}/{}): {}", url, attempt + 1, props.getMaxRetries() + 1, e.toString());
        lastError = e;
        sleep(500L * (attempt + 1));
      }
    }
    log.error("Giving up on {} after {} attempts", url, props.getMaxRetries() + 1);
    throw new RuntimeException("Failed to fetch patch: " + url, lastError);
  }

  private static long parseRetryAfter(HttpResponse<String> response, int attempt) {
    return response.headers().firstValueAsLong("retry-after").orElse((long) Math.pow(2, attempt) * 2);
  }

  private static void sleep(long millis) {
    if (millis <= 0) return;
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @PreDestroy
  public void shutdown() {
    pool.shutdown();
  }
}
