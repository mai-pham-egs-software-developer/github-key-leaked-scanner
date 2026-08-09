package com.leakscanner.crypto.chain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.leakscanner.crypto.Chain;
import com.leakscanner.crypto.ChainEco;
import com.leakscanner.crypto.rpc.Web3ConnectionPool;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.web3j.protocol.Web3j;

/**
 * Exercises the logic shared in {@link AbstractChainResolver} through both of
 * its current implementations ({@link Btc} and {@link Eth}), so the base
 * class's behavior stays consistent across chains as more get added.
 */
class AbstractChainResolverTest {

  // pk = 1 is a well-known public test vector; its EIP-55 address is 0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf.
  private static final String PK_ONE =
      "0000000000000000000000000000000000000000000000000000000000000001";
  private static final String PK_ONE_ADDRESS = "0x7E5F4552091A69125d5DfCb7b8C2659029395Bdf";

  static Stream<AbstractChainResolver> resolvers() {
    return Stream.of(new Btc(mock(Web3ConnectionPool.class)), new Eth(mock(Web3ConnectionPool.class)));
  }

  @ParameterizedTest
  @MethodSource("resolvers")
  void resolveAddressDerivesTheKnownEip55AddressForBothImplementations(AbstractChainResolver resolver) {
    assertThat(resolver.resolveAddress(PK_ONE)).isEqualTo(PK_ONE_ADDRESS);
  }

  @ParameterizedTest
  @MethodSource("resolvers")
  void resolveAddressIgnoresALeading0xPrefix(AbstractChainResolver resolver) {
    assertThat(resolver.resolveAddress("0x" + PK_ONE)).isEqualTo(resolver.resolveAddress(PK_ONE));
  }

  @ParameterizedTest
  @MethodSource("resolvers")
  void resolveAddressIsDeterministic(AbstractChainResolver resolver) {
    assertThat(resolver.resolveAddress(PK_ONE)).isEqualTo(resolver.resolveAddress(PK_ONE));
  }

  @Test
  void ethResolverReportsTheEthEcosystem() {
    assertThat(new Eth(mock(Web3ConnectionPool.class)).chain()).isEqualTo(ChainEco.ETH);
  }

  @Test
  void btcResolverReportsTheBtcEcosystem() {
    assertThat(new Btc(mock(Web3ConnectionPool.class)).chain()).isEqualTo(ChainEco.BTC);
  }

  @Test
  void web3jDelegatesToThePoolForTheRequestedRpcChain() {
    Web3ConnectionPool pool = mock(Web3ConnectionPool.class);
    Web3j fakeConnection = mock(Web3j.class);
    when(pool.getConnection(Chain.ETH_MAINNET)).thenReturn(fakeConnection);

    Eth eth = new Eth(pool);

    assertThat(eth.web3j(Chain.ETH_MAINNET)).isSameAs(fakeConnection);
    verify(pool).getConnection(Chain.ETH_MAINNET);
  }

  @Test
  void web3jIsSharedInfraAvailableToAnySubclassRegardlessOfItsEcosystem() {
    // AbstractChainResolver.web3j(Chain) doesn't care which ecosystem the resolver belongs
    // to — Btc has no RPC network of its own yet, so it's exercised here with Chain.ETH_MAINNET
    // purely as a stand-in value to prove the base class's delegation is chain-agnostic.
    Web3ConnectionPool pool = mock(Web3ConnectionPool.class);
    Web3j fakeConnection = mock(Web3j.class);
    when(pool.getConnection(Chain.ETH_MAINNET)).thenReturn(fakeConnection);

    Btc btc = new Btc(pool);

    assertThat(btc.web3j(Chain.ETH_MAINNET)).isSameAs(fakeConnection);
    verify(pool).getConnection(Chain.ETH_MAINNET);
  }

  @Test
  void invalidateConnectionDelegatesToThePoolForTheRequestedRpcChain() {
    Web3ConnectionPool pool = mock(Web3ConnectionPool.class);
    Eth eth = new Eth(pool);

    eth.invalidateConnection(Chain.ETH_MAINNET);

    verify(pool).invalidate(Chain.ETH_MAINNET);
  }

  @Test
  void invalidateConnectionIsSharedInfraAvailableToAnySubclass() {
    Web3ConnectionPool pool = mock(Web3ConnectionPool.class);
    Btc btc = new Btc(pool);

    btc.invalidateConnection(Chain.ETH_MAINNET);

    verify(pool).invalidate(Chain.ETH_MAINNET);
  }

  @Test
  void defaultRetrieveStubReturnsNullWhenASubclassDoesNotOverrideIt() {
    Btc btc = new Btc(mock(Web3ConnectionPool.class));

    assertThat(btc.retrieve(PK_ONE)).isNull();
  }
}
