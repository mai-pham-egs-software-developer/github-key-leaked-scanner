package com.leakscanner.crypto.rpc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pure-logic coverage for the round-robin rotation used by {@link RpcManagement#getHealthyRpcUrl},
 * exercised directly so it doesn't depend on chainlist.org or live health checks.
 */
class RpcManagementTest {

  private final RpcManagement rpcManagement = new RpcManagement();

  @Test
  void rotateAdvancesTheStartingPointOnEachCallForTheSameChainId() {
    List<String> candidates = List.of("a", "b", "c", "d");

    assertThat(rpcManagement.rotate(candidates, 1)).containsExactly("a", "b", "c", "d");
    assertThat(rpcManagement.rotate(candidates, 1)).containsExactly("b", "c", "d", "a");
    assertThat(rpcManagement.rotate(candidates, 1)).containsExactly("c", "d", "a", "b");
    assertThat(rpcManagement.rotate(candidates, 1)).containsExactly("d", "a", "b", "c");
    assertThat(rpcManagement.rotate(candidates, 1)).containsExactly("a", "b", "c", "d"); // wraps around
  }

  @Test
  void rotateCursorsAreIndependentPerChainId() {
    List<String> candidates = List.of("a", "b", "c");

    rpcManagement.rotate(candidates, 1);
    rpcManagement.rotate(candidates, 1);

    assertThat(rpcManagement.rotate(candidates, 2)).containsExactly("a", "b", "c");
  }

  @Test
  void rotateHandlesASingleCandidateWithoutError() {
    List<String> candidates = List.of("only");

    assertThat(rpcManagement.rotate(candidates, 1)).containsExactly("only");
    assertThat(rpcManagement.rotate(candidates, 1)).containsExactly("only");
  }
}
