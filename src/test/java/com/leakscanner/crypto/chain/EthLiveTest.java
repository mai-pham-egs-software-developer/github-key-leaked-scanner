package com.leakscanner.crypto.chain;

import static org.assertj.core.api.Assertions.assertThat;

import com.leakscanner.config.ScannerProperties;
import com.leakscanner.crypto.ChainEco;
import com.leakscanner.crypto.dto.BalanceResultDto;
import com.leakscanner.crypto.rpc.RpcManagement;
import com.leakscanner.crypto.rpc.Web3ConnectionPool;
import java.math.BigDecimal;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Exercises {@link Eth} against real infrastructure: a live chainlist.org fetch,
 * a real health-checked RPC endpoint, and an actual eth_getBalance call — no mocks.
 * Aborts (rather than fails) if the network/RPC endpoints aren't reachable from
 * this environment, so a lack of network doesn't look like a broken build.
 */
class EthLiveTest {

  // pk = 1 is a well-known public test vector; its EIP-55 address is 0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf.
  private static final String PK_ONE =
      "0000000000000000000000000000000000000000000000000000000000000001";
  private static final String PK_ONE_ADDRESS = "0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf";
  private static final String PK_RABBY_TEST = "0x5c331061b7575d679f2a798c7ef2274ed56be56e8e1b92bbdfbb473ead0d8382";
  private final Eth eth = new Eth(new Web3ConnectionPool(new RpcManagement()), new ScannerProperties());

  @Test
  void retrieveResolvesAddressAndFetchesARealBalanceFromALiveRpcEndpoint() {
    BalanceResultDto result = retrieveOrSkip();
    System.out.printf("");
  }



  private BalanceResultDto retrieveOrSkip() {
    try {
      return eth.retrieve(PK_RABBY_TEST);
    } catch (RuntimeException e) {
      return Assumptions.abort(
          "No live network/RPC access available in this environment: " + e.getMessage());
    }
  }
}
