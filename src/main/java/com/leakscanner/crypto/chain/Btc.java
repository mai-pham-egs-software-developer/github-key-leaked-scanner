package com.leakscanner.crypto.chain;

import com.leakscanner.crypto.ChainEco;
import com.leakscanner.crypto.rpc.Web3ConnectionPool;
import org.springframework.stereotype.Component;

@Component
public class Btc extends AbstractChainResolver {

    public Btc(Web3ConnectionPool connectionPool) {
        super(connectionPool);
    }

    @Override
    public ChainEco chain() {
        return ChainEco.BTC;
    }
}
