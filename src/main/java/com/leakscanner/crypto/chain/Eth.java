package com.leakscanner.crypto.chain;

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

    public Eth(Web3ConnectionPool connectionPool) {
        super(connectionPool);
    }

    @Override
    public ChainEco chain() {
        return ChainEco.ETH;
    }

    @Override
    public BalanceResultDto retrieve(String pk) {
        String address = resolveAddress(pk);

        BigInteger balanceWei;
        try {
            balanceWei = web3j(Chain.ETH_MAINNET).ethGetBalance(address, DefaultBlockParameterName.LATEST)
                    .send()
                    .getBalance();
        } catch (IOException e) {
            invalidateConnection(Chain.ETH_MAINNET);
            throw new RuntimeException("Failed to fetch ETH balance for " + address, e);
        }

        BalanceResultDto result = new BalanceResultDto();
        result.setChain(ChainEco.ETH);
        result.setAddress(address);
        result.setBalance(Convert.fromWei(new BigDecimal(balanceWei), Convert.Unit.ETHER));
        return result;
    }
}
