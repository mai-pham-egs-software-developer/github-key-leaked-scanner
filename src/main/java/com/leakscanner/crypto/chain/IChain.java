package com.leakscanner.crypto.chain;

import com.leakscanner.crypto.Chain;
import com.leakscanner.crypto.ChainEco;
import com.leakscanner.crypto.dto.BalanceResultDto;

public interface IChain {

    Chain chain();

    String resolveAddress(String pk);

    BalanceResultDto retrieve(String pk);

}
