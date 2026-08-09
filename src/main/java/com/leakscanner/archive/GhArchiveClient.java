package com.leakscanner.archive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leakscanner.config.ScannerProperties;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import org.springframework.stereotype.Component;

/** Downloads (and caches) hourly GH Archive files, streaming out only PushEvents. */
@Component
public class GhArchiveClient {

  private static final String BASE_URL = "https://data.gharchive.org";

  private final ScannerProperties props;
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient http = HttpClient.newHttpClient();

  public GhArchiveClient(ScannerProperties props) {
    this.props = props;
  }

  public String hourKey(String date, int hour) {
    return date + "-" + hour;
  }

  private Path cacheDir() {
    return Path.of(props.getDataDir(), "archive-cache");
  }

  private Path cachePath(String date, int hour) {
    return cacheDir().resolve(hourKey(date, hour) + ".json.gz");
  }

  private Path ensureCached(String date, int hour) throws IOException, InterruptedException {
    Path dest = cachePath(date, hour);
    if (Files.exists(dest)) return dest;

    Files.createDirectories(cacheDir());
    String url = BASE_URL + "/" + date + "-" + hour + ".json.gz";
    HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
    Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
    HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(tmp));
    if (response.statusCode() != 200) {
      Files.deleteIfExists(tmp);
      throw new IOException("Failed to download " + url + ": HTTP " + response.statusCode());
    }
    Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
    return dest;
  }

  /** Streams PushEvents for one UTC hour to the given consumer without loading the whole hour into memory. */
  public void forEachPushEvent(String date, int hour, Consumer<PushEvent> consumer)
      throws IOException, InterruptedException {
    Path file = ensureCached(date, hour);
    try (var gzip = new GZIPInputStream(Files.newInputStream(file));
        var reader = new BufferedReader(new InputStreamReader(gzip, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank()) continue;
        JsonNode evt;
        try {
          evt = mapper.readTree(line);
        } catch (Exception e) {
          continue; // malformed line, skip
        }
        if (!"PushEvent".equals(evt.path("type").asText())) continue;

        String repoName = evt.path("repo").path("name").asText(null);
        JsonNode payload = evt.path("payload");
        String head = payload.path("head").asText(null);
        if (repoName == null || head == null) continue;

        String before = payload.path("before").asText(null);
        String createdAt = evt.path("created_at").asText(null);
        consumer.accept(new PushEvent(repoName, before, head, createdAt));
      }
    }
  }
}
