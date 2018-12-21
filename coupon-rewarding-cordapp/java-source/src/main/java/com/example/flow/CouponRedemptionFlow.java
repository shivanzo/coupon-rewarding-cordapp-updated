package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.CouponContract;
import com.example.state.CouponState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.jetbrains.annotations.NotNull;
import static net.corda.core.contracts.ContractsDSL.requireThat;

import java.util.List;


public class CouponRedemptionFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final Party netmedsParty;
        private boolean isCouponUtilized;
        UniqueIdentifier coupounId;
        private int amount;

        public Initiator(Party netmedsParty, UniqueIdentifier coupounId) {
            this.netmedsParty = netmedsParty;
            this.coupounId = coupounId;
        }

        public Party getNetmedsParty() {
            return netmedsParty;
        }

        public boolean isCouponUtilized() {
            return isCouponUtilized;
        }

        public UniqueIdentifier getCoupounId() {
            return coupounId;
        }

        public int getAmount() {
            return amount;
        }

        public void setCouponUtilized(boolean couponUtilized) {
            isCouponUtilized = couponUtilized;
        }

        public void setCoupounId(UniqueIdentifier coupounId) {
            this.coupounId = coupounId;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final ProgressTracker.Step VERIFYING_COUPON = new ProgressTracker.Step("Response from credit rating agency about loan eligibility and approval");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.") {
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };
        private final ProgressTracker progressTracker = new ProgressTracker(
                VERIFYING_TRANSACTION,
                VERIFYING_COUPON,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );



        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {

            CouponState couponState = null;
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            Party amazon = getServiceHub().getMyInfo().getLegalIdentities().get(0);
            StateAndRef<CouponState> inputState = null;

            QueryCriteria criteriaCouponState = new QueryCriteria.LinearStateQueryCriteria(
                    null,
                    ImmutableList.of(coupounId),
                    Vault.StateStatus.CONSUMED,
                    null
            );

            List<StateAndRef<CouponState>> inputStateList = getServiceHub().getVaultService().queryBy(CouponState.class, criteriaCouponState).getStates();
            if (inputStateList == null || inputStateList.isEmpty()) {
                throw new FlowException("State with coupon id cannot be found : " + inputStateList.size() + " " + coupounId);
            }

            inputState = inputStateList.get(0);
            couponState = new CouponState(netmedsParty, amazon, amount, coupounId, true, true);

            progressTracker.setCurrentStep(VERIFYING_COUPON);

            final Command<CouponContract.Commands.CouponVerification> couponVerificationCommand = new Command<CouponContract.Commands.CouponVerification>(new CouponContract.Commands.CouponVerification(), ImmutableList.of(couponState.getInitiatingParty().getOwningKey(), couponState.getCounterParty().getOwningKey()));

            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addOutputState(couponState, CouponContract.COUPON_CONTRACT_ID)
                    .addCommand(couponVerificationCommand);

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);

            txBuilder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);

            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            progressTracker.setCurrentStep(GATHERING_SIGS);

            FlowSession otherPartySession = initiateFlow(netmedsParty);

            final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(partSignedTx, ImmutableSet.of(otherPartySession), CollectSignaturesFlow.Companion.tracker()));

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);

            return subFlow(new FinalityFlow(fullySignedTx));
        }
    }


    @InitiatedBy(Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction> {

        public final FlowSession otherPartyFlow;

        public Acceptor(FlowSession otherPartyFlow) {
            this.otherPartyFlow = otherPartyFlow;
        }


        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {

            class SignTxFlow extends  SignTransactionFlow {

                public SignTxFlow(FlowSession otherSideSession, ProgressTracker progressTracker) {
                    super(otherSideSession, progressTracker);
                }

                @Override
                protected void checkTransaction(@NotNull SignedTransaction stx) throws FlowException {

                    requireThat(require -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("This must be of the type CouponState ", output instanceof CouponState);
                        return null;
                    });
                }
            }
            return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
        }
    }

}
