package com.leakscanner.crypto;

public enum Chain {

    ETH_MAINNET(1, ChainEco.ETH);

    private final long chainId;
    private final ChainEco chain;

    Chain(long chainId, ChainEco chain) {
        this.chainId = chainId;
        this.chain = chain;
    }

    /** EVM chainId per chainlist.org. */
    public long chainId() {
        return chainId;
    }

    public ChainEco chain() {
        return chain;
    }
}
