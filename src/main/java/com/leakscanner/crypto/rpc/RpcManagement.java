package com.leakscanner.crypto.rpc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leakscanner.crypto.Chain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Sources candidate JSON-RPC endpoints per EVM chainId from chainlist.org and
 * hands out one that just passed a live health check. A failed endpoint goes
 * on a short cooldown rather than a permanent blacklist, since public RPC
 * nodes routinely blip and recover.
 */
@Component
public class RpcManagement {

    private static final Logger log = LoggerFactory.getLogger(RpcManagement.class);

    private static final String CHAINLIST_URL = "https://chainlist.org/rpcs.json";
    private static final Duration CHAINLIST_REFRESH_INTERVAL = Duration.ofHours(6);
    private static final Duration HEALTH_CHECK_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration FAILURE_COOLDOWN = Duration.ofMinutes(5);
    private static final int MAX_CANDIDATES_TO_PROBE = 8;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HEALTH_CHECK_TIMEOUT)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicReference<Map<Long, List<String>>> candidatesByChainId = new AtomicReference<>(Map.of());
    private volatile Instant chainlistFetchedAt = Instant.EPOCH;

    private final Map<String, Instant> cooldownUntil = new ConcurrentHashMap<>();
    private final Map<Long, AtomicInteger> roundRobinCursor = new ConcurrentHashMap<>();

    /** Returns a currently healthy RPC URL for the given chain, or throws if none can be found. */
    public String getHealthyRpcUrl(Chain chain) {
        refreshChainlistIfStale();

        List<String> candidates = candidatesByChainId.get().getOrDefault(chain.chainId(), List.of());
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No RPC candidates known for " + chain);
        }

        // Round-robin: rotate the starting point each call so repeated acquisitions (e.g. after
        // a pool invalidation) spread across the known candidates instead of always probing the
        // same front-of-list endpoint first.
        List<String> rotated = rotate(candidates, chain.chainId());

        Instant now = Instant.now();
        List<String> probeable = rotated.stream()
                .filter(url -> !isOnCooldown(url, now))
                .limit(MAX_CANDIDATES_TO_PROBE)
                .collect(Collectors.toList());

        for (String url : probeable) {
            if (isHealthy(url)) {
                return url;
            }
            reportFailure(url);
        }

        log.warn("No healthy RPC endpoint found for {} (probed {} of {} known candidates)",
                chain, probeable.size(), candidates.size());
        throw new IllegalStateException("No healthy RPC endpoint found for " + chain
                + " (probed " + probeable.size() + " of " + candidates.size() + " known candidates)");
    }

    /** Returns candidates rotated to start after wherever the last call for this chainId left off. */
    List<String> rotate(List<String> candidates, long chainId) {
        int size = candidates.size();
        AtomicInteger cursor = roundRobinCursor.computeIfAbsent(chainId, id -> new AtomicInteger(0));
        int start = Math.floorMod(cursor.getAndIncrement(), size);

        List<String> rotated = new ArrayList<>(size);
        rotated.addAll(candidates.subList(start, size));
        rotated.addAll(candidates.subList(0, start));
        return rotated;
    }

    /** Puts an endpoint on cooldown so the next {@link #getHealthyRpcUrl} skips it for a while. */
    public void reportFailure(String url) {
        cooldownUntil.put(url, Instant.now().plus(FAILURE_COOLDOWN));
    }

    private boolean isOnCooldown(String url, Instant now) {
        Instant until = cooldownUntil.get(url);
        return until != null && now.isBefore(until);
    }

    private void refreshChainlistIfStale() {
        if (Instant.now().isBefore(chainlistFetchedAt.plus(CHAINLIST_REFRESH_INTERVAL))) {
            return;
        }
        synchronized (this) {
            if (Instant.now().isBefore(chainlistFetchedAt.plus(CHAINLIST_REFRESH_INTERVAL))) {
                return;
            }
            try {
                candidatesByChainId.set(fetchChainlist());
                chainlistFetchedAt = Instant.now();
            } catch (Exception e) {
                if (candidatesByChainId.get().isEmpty()) {
                    throw new IllegalStateException("Failed to fetch RPC endpoint list from chainlist.org", e);
                }
                // Already have a cached list from a previous fetch — keep serving that instead of failing outright.
            }
        }
    }

    private Map<Long, List<String>> fetchChainlist() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(CHAINLIST_URL))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("chainlist.org returned HTTP " + response.statusCode());
        }

        List<ChainlistEntry> entries = objectMapper.readValue(response.body(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, ChainlistEntry.class));

        return entries.stream()
                .collect(Collectors.toMap(
                        ChainlistEntry::getChainId,
                        entry -> entry.getRpc().stream()
                                .map(ChainlistEntry.RpcEntry::getUrl)
                                .filter(url -> url != null && url.startsWith("https://"))
                                .collect(Collectors.toList()),
                        (a, b) -> a));
    }

    private boolean isHealthy(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(HEALTH_CHECK_TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\",\"params\":[],\"id\":1}"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            boolean healthy = response.statusCode() == 200
                    && response.body().contains("\"result\":\"0x")
                    && !response.body().contains("\"error\"");
            if (!healthy) {
                log.debug("RPC health check failed for {}: HTTP {} body={}", url, response.statusCode(), response.body());
            }
            return healthy;
        } catch (Exception e) {
            log.debug("RPC health check failed for {}: {}", url, e.toString());
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChainlistEntry {
        private long chainId;
        private List<RpcEntry> rpc = List.of();

        public long getChainId() {
            return chainId;
        }

        public void setChainId(long chainId) {
            this.chainId = chainId;
        }

        public List<RpcEntry> getRpc() {
            return rpc;
        }

        public void setRpc(List<RpcEntry> rpc) {
            this.rpc = rpc;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class RpcEntry {
            private String url;

            public String getUrl() {
                return url;
            }

            public void setUrl(String url) {
                this.url = url;
            }
        }
    }
}
