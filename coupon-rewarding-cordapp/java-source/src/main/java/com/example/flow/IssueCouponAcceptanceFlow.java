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

import static net.corda.core.contracts.ContractsDSL.requireThat;


import java.util.List;

public class IssueCouponAcceptanceFlow {

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private Party couponIssuerParty;
        private boolean isCouponUtilized;
        UniqueIdentifier coupounId;
        private int amount;
        private boolean isCouponApproved;
        private String userName;
        private String couponName;

        public Initiator(UniqueIdentifier couponId) {
            this.coupounId = couponId;
        }

        public Party getCouponIssuerParty() {
            return couponIssuerParty;
        }

        public boolean isCouponUtilized() {
            return isCouponUtilized;
        }

        public UniqueIdentifier getCoupounId() {
            return coupounId;
        }

        public void setCouponUtilized(boolean couponUtilized) {
            isCouponUtilized = couponUtilized;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public void setCoupounId(UniqueIdentifier coupounId) {
            this.coupounId = coupounId;
        }

        public boolean isCouponApproved() {
            return isCouponApproved;
        }

        public void setCouponApproved(boolean couponApproved) {
            isCouponApproved = couponApproved;
        }

        public String getUserName() {
            return userName;
        }

        public void setCouponName(String couponName) {
            this.couponName = couponName;
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

            getStateMachine().getLogger().info("Shivan Sawant");
            getStateMachine().getLogger().info("Start of flow  : CouponAcceptance flow");

            CouponState couponState = null;
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            Party vendorParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);
            StateAndRef<CouponState> inputState = null;

            getStateMachine().getLogger().info("Querying Previous Unconsumed State");

            QueryCriteria criteriaCouponState = new QueryCriteria.LinearStateQueryCriteria(
                    null,
                    ImmutableList.of(coupounId),
                    Vault.StateStatus.UNCONSUMED,
                    null
            );

            List<StateAndRef<CouponState>> inputStateList = getServiceHub().getVaultService().queryBy(CouponState.class, criteriaCouponState).getStates();

            if (inputStateList == null || inputStateList.isEmpty()) {
                throw new IllegalArgumentException("State with coupon id cannot be found : " + inputStateList.size() + " " + coupounId + "Vendor Party : " + vendorParty);
            }

            inputState = inputStateList.get(0);
            amount = inputStateList.get(0).getState().getData().getAmount();
            userName = inputStateList.get(0).getState().getData().getUsername();
            couponIssuerParty = inputStateList.get(0).getState().getData().getInitiatingParty();
            couponName = inputStateList.get(0).getState().getData().getCouponName();

            getStateMachine().getLogger().info("Previous unconsumed state : " + inputState );

            getStateMachine().getLogger().info("Querying parameters from previous state : amount : " + amount + " " + "username : " + userName + " " + "couponIssuerParty : " + couponIssuerParty + " " + "couponname : " + couponName);

            couponState = new CouponState(couponIssuerParty, vendorParty, amount, coupounId, false, true, userName, couponName);

            progressTracker.setCurrentStep(VERIFYING_COUPON);


            final Command<CouponContract.Commands.CouponVerification> couponVerificationCommand = new Command<CouponContract.Commands.CouponVerification>(new CouponContract.Commands.CouponVerification(), ImmutableList.of(couponState.getInitiatingParty().getOwningKey(), couponState.getCounterParty().getOwningKey()));

            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(inputState)
                    .addOutputState(couponState, CouponContract.COUPON_CONTRACT_ID)
                    .addCommand(couponVerificationCommand);

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);

            getStateMachine().getLogger().info("VERIFYING TRANSACTION");

            getStateMachine().getLogger().info("VERIFYING TRANSACTION ACCORDING TO CONTRACT");

            txBuilder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);

            getStateMachine().getLogger().info("SIGNING_TRANSACTION");

            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            getStateMachine().getLogger().info("GATHERING_SIGNATURES \n");

            progressTracker.setCurrentStep(GATHERING_SIGS);

            FlowSession otherPartySession = initiateFlow(couponIssuerParty);

            final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(partSignedTx, ImmutableSet.of(otherPartySession), CollectSignaturesFlow.Companion.tracker()));

            //stage 5
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);

            getStateMachine().getLogger().info("FINALISING TRANSACTION \n ");

            //Notarise and record the transaction in both party vaults.
            return subFlow(new FinalityFlow(fullySignedTx));

        }

    }

    @InitiatedBy(Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction> {
        private final FlowSession otherPartyFlow;

        public Acceptor(FlowSession otherPartyFlow) {
            this.otherPartyFlow = otherPartyFlow;
        }
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends  SignTransactionFlow {
                public SignTxFlow(FlowSession otherSideSession, ProgressTracker progressTracker) {
                    super(otherSideSession, progressTracker);
                }
                @Override
                protected void checkTransaction(SignedTransaction stx) throws FlowException
                {
                    requireThat(require -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("This must be of the type CouponState.", output instanceof CouponState);
                        return null;
                    });
                }
            }
            return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
        }
    }
}
