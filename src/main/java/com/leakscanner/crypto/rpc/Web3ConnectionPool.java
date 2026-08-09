package com.leakscanner.crypto.rpc;

import com.leakscanner.crypto.Chain;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches one Web3j client per {@link Chain}, backed by a healthy endpoint chosen
 * by {@link RpcManagement}. This isn't a traditional acquire/release pool —
 * Web3j's HttpService is a thin, reusable HTTP client — so "pooling" here
 * means picking a good endpoint once and reusing it until it starts failing.
 */
@Component
public class Web3ConnectionPool {

    private record PooledConnection(String url, Web3j web3j) {
    }

    private final RpcManagement rpcManagement;
    private final Map<Chain, PooledConnection> connections = new ConcurrentHashMap<>();

    public Web3ConnectionPool(RpcManagement rpcManagement) {
        this.rpcManagement = rpcManagement;
    }

    /** Returns the pooled Web3j client for this chain, opening one if none is cached yet. */
    public Web3j getConnection(Chain chain) {
        return connections.computeIfAbsent(chain, this::openConnection).web3j();
    }

    /** Drops the cached connection for this chain and reports its endpoint as failed, so the next getConnection picks a different one. */
    public synchronized void invalidate(Chain chain) {
        PooledConnection existing = connections.remove(chain);
        if (existing != null) {
            rpcManagement.reportFailure(existing.url());
            existing.web3j().shutdown();
        }
    }

    private PooledConnection openConnection(Chain chain) {
        String url = rpcManagement.getHealthyRpcUrl(chain);
        return new PooledConnection(url, Web3j.build(new HttpService(url)));
    }
}
