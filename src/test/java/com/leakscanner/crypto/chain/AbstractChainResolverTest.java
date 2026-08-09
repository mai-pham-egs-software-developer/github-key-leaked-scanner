package com.leakscanner.crypto.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leakscanner.crypto.Chain;
import com.leakscanner.crypto.ChainEco;
import com.leakscanner.crypto.dto.BalanceResultDto;
import com.leakscanner.crypto.rpc.Web3ConnectionPool;
import java.io.IOException;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.methods.response.EthGetBalance;

/**
 * Exercises {@link AbstractChainResolver}'s shared logic (address derivation, pool
 * delegation, retrieve()) through its one current implementation, {@link Eth}.
 */
class AbstractChainResolverTest {

  // pk = 1 is a well-known public test vector; its EIP-55 address is 0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf.
  private static final String PK_ONE =
      "0000000000000000000000000000000000000000000000000000000000000001";
  private static final String PK_ONE_ADDRESS = "0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf";

  @Test
  void resolveAddressDerivesTheKnownEip55Address() {
    Eth eth = new Eth(mock(Web3ConnectionPool.class));

    assertThat(eth.resolveAddress(PK_ONE)).isEqualTo(PK_ONE_ADDRESS);
  }

  @Test
  void resolveAddressIgnoresALeading0xPrefix() {
    Eth eth = new Eth(mock(Web3ConnectionPool.class));

    assertThat(eth.resolveAddress("0x" + PK_ONE)).isEqualTo(eth.resolveAddress(PK_ONE));
  }

  @Test
  void resolveAddressIsDeterministic() {
    Eth eth = new Eth(mock(Web3ConnectionPool.class));

    assertThat(eth.resolveAddress(PK_ONE)).isEqualTo(eth.resolveAddress(PK_ONE));
  }

  @Test
  void ethResolverReportsTheEthMainnetChainAndEcosystem() {
    Eth eth = new Eth(mock(Web3ConnectionPool.class));

    assertThat(eth.chain()).isEqualTo(Chain.ETH_MAINNET);
    assertThat(eth.chain().chain()).isEqualTo(ChainEco.ETH);
  }

  @Test
  void web3jDelegatesToThePoolForTheResolversChain() {
    Web3ConnectionPool pool = mock(Web3ConnectionPool.class);
    Web3j fakeConnection = mock(Web3j.class);
    when(pool.getConnection(Chain.ETH_MAINNET)).thenReturn(fakeConnection);

    Eth eth = new Eth(pool);

    assertThat(eth.web3j(Chain.ETH_MAINNET)).isSameAs(fakeConnection);
    verify(pool).getConnection(Chain.ETH_MAINNET);
  }

  @Test
  void invalidateConnectionDelegatesToThePoolForTheResolversChain() {
    Web3ConnectionPool pool = mock(Web3ConnectionPool.class);
    Eth eth = new Eth(pool);

    eth.invalidateConnection(Chain.ETH_MAINNET);

    verify(pool).invalidate(Chain.ETH_MAINNET);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void retrieveReturnsTheAddressAndBalanceFromThePooledConnection() throws Exception {
    Web3ConnectionPool pool = mock(Web3ConnectionPool.class);
    Web3j web3j = mock(Web3j.class);
    Request request = mock(Request.class);
    EthGetBalance ethGetBalance = mock(EthGetBalance.class);

    when(pool.getConnection(Chain.ETH_MAINNET)).thenReturn(web3j);
    when(web3j.ethGetBalance(eq(PK_ONE_ADDRESS), any())).thenReturn(request);
    when(request.send()).thenReturn(ethGetBalance);
    when(ethGetBalance.getBalance()).thenReturn(BigInteger.valueOf(1_000_000_000_000_000_000L)); // 1 ETH

    Eth eth = new Eth(pool);
    BalanceResultDto result = eth.retrieve(PK_ONE);

    assertThat(result.getChain()).isEqualTo(ChainEco.ETH);
    assertThat(result.getAddress()).isEqualTo(PK_ONE_ADDRESS);
    assertThat(result.getBalance()).isEqualByComparingTo("1");
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void retrieveInvalidatesTheConnectionWhenTheRpcCallFails() throws Exception {
    Web3ConnectionPool pool = mock(Web3ConnectionPool.class);
    Web3j web3j = mock(Web3j.class);
    Request request = mock(Request.class);

    when(pool.getConnection(Chain.ETH_MAINNET)).thenReturn(web3j);
    when(web3j.ethGetBalance(any(), any())).thenReturn(request);
    when(request.send()).thenThrow(new IOException("rpc down"));

    Eth eth = new Eth(pool);

    assertThatThrownBy(() -> eth.retrieve(PK_ONE)).isInstanceOf(RuntimeException.class);
    verify(pool).invalidate(Chain.ETH_MAINNET);
  }
}
