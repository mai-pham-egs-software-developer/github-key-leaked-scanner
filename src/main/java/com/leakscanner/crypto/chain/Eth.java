package com.leakscanner.crypto.chain;

import com.leakscanner.config.ScannerProperties;
import com.leakscanner.crypto.Chain;
import com.leakscanner.crypto.ChainEco;
import com.leakscanner.crypto.dto.BalanceResultDto;
import com.leakscanner.crypto.rpc.Web3ConnectionPool;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.utils.Convert;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Resolves an ETH balance for a private key via a JSON-RPC node (eth_getBalance),
 * using a pooled Web3j connection instead of talking to a fixed endpoint.
 */
@Component
public class Eth extends AbstractChainResolver {

    public Eth(Web3ConnectionPool connectionPool, ScannerProperties props) {
        super(connectionPool, props);
    }

    @Override
    public Chain chain() {
        return Chain.ETH_MAINNET;
    }

}
