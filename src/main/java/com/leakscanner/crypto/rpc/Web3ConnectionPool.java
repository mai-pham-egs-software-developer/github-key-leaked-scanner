package com.leakscanner.crypto.rpc;

import com.leakscanner.crypto.Chain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

/**
 * Hands out a new Web3j client on every call, backed by a healthy endpoint that
 * {@link RpcManagement} rotates round-robin. Deliberately not cached per {@link Chain} —
 * spreading requests across every known-good RPC endpoint matters more here than reusing
 * one connection, since a single public node getting all the traffic risks rate-limiting.
 */
@Component
public class Web3ConnectionPool {

    private static final Logger log = LoggerFactory.getLogger(Web3ConnectionPool.class);

    public record Connection(String url, Web3j web3j) {
    }

    private final RpcManagement rpcManagement;

    public Web3ConnectionPool(RpcManagement rpcManagement) {
        this.rpcManagement = rpcManagement;
    }

    /** Opens a new client against a currently-healthy, round-robin-rotated endpoint for this chain. */
    public Connection getConnection(Chain chain) {
        String url = rpcManagement.getHealthyRpcUrl(chain);
        log.info("Using RPC endpoint {} for {}", url, chain);
        return new Connection(url, Web3j.build(new HttpService(url)));
    }

    /** Reports this connection's endpoint as failed, so it sits out the next few lookups on cooldown. */
    public void invalidate(Connection connection) {
        rpcManagement.reportFailure(connection.url());
    }
}
