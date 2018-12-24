
package com.example.flow;

import com.example.state.CouponState;
import com.google.common.collect.ImmutableList;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.*;
import net.corda.core.transactions.SignedTransaction;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class IssueCouponAcceptanceFlowTest {

    private MockNetwork network;
    private StartedMockNode nodeA;
    private StartedMockNode nodeB;

    private static int amount = 2000;


    @Before
    public void setup() {
        network = new MockNetwork(ImmutableList.of("com.example.contract"));
        nodeA = network.createPartyNode(null);
        nodeB = network.createPartyNode(null);
        for (StartedMockNode node : ImmutableList.of(nodeA, nodeB)) {
            node.registerInitiatedFlow(IssueCouponRequestFlow.Acceptor.class);
        }
        network.runNetwork();
    }

    @Rule
    public final ExpectedException exception = ExpectedException.none();

    @Test
    public void recordedTransactionHasOneInputsAndASingleOutputTheInputIOU() throws Exception {

        IssueCouponAcceptanceFlow.Initiator flow = new IssueCouponAcceptanceFlow.Initiator( new UniqueIdentifier());
        CordaFuture<SignedTransaction> future = nodeB.startFlow(flow);
        network.runNetwork();
        SignedTransaction signedTx = future.get();

        // We check the recorded transaction in both vaults.
        for (StartedMockNode node : ImmutableList.of(nodeA, nodeB)) {
            SignedTransaction recordedTx = node.getServices().getValidatedTransactions().getTransaction(signedTx.getId());
            List<TransactionState<ContractState>> txOutputs = recordedTx.getTx().getOutputs();
            List<StateRef> txInputs = recordedTx.getTx().getInputs();
            assert (((List) txOutputs).size() == 1);
            assert (((List)txInputs).size() == 1);

            CouponState recordedState = (CouponState) txOutputs.get(0).getData();
            assertEquals(recordedState.getInitiatingParty(), nodeB.getInfo().getLegalIdentities().get(0));
            assertEquals(recordedState.getCounterParty(), nodeA.getInfo().getLegalIdentities().get(0));
        }
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }
}

