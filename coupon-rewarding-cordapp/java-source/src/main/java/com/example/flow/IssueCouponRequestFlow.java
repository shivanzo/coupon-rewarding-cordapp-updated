package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.CouponContract;
import com.example.state.CouponState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.ContractState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class IssueCouponRequestFlow {


    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final Party couponVendorParty;
        private int amount;
        private UniqueIdentifier couponId;
        private boolean isCouponApproved;
        private String userName;
        private String couponName;

        public Initiator(Party couponVendorParty, int amount, String userName, String couponName) {
            this.couponVendorParty = couponVendorParty;
            this.amount = amount;
            this.userName = userName;
            this.couponName = couponName;
        }

        public Party getCouponVendorParty() {
            return couponVendorParty;
        }

        public int getAmount() {
            return amount;
        }

        public UniqueIdentifier getCouponId() {
            return couponId;
        }

        public void setAmount(int amount) {
            this.amount = amount;
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

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getCouponName() {
            return couponName;
        }

        public void setCouponName(String couponName) {
            this.couponName = couponName;
        }

        @Override
        @Suspendable
        public SignedTransaction call() throws FlowException {

            getStateMachine().getLogger().info("Shivan Sawant");
            getStateMachine().getLogger().info("Start of flow  : CouponIssueFlow");

            getStateMachine().getLogger().info("Selecting notary from available NetworkMapCache  : CouponIssueFlow");

            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            getStateMachine().getLogger().info("Notary selected from networkMapCache " + notary);

           /* progressTracker.setCurrentStep(COUPON_GENERATION);*/

            getStateMachine().getLogger().info("COUPON GENERATION");

            //Generate an unsigned transaction
            Party couponIssuerParty = getServiceHub().getMyInfo().getLegalIdentities().get(0);

            getStateMachine().getLogger().info("Getting CouponIssuerParty : " + couponIssuerParty);

            CouponState couponState = new CouponState(couponIssuerParty, couponVendorParty, amount, new UniqueIdentifier(), false, false, userName, couponName);

            final Command<CouponContract.Commands.CouponGeneration> couponGenerationCommand = new Command<CouponContract.Commands.CouponGeneration>(new CouponContract.Commands.CouponGeneration(), ImmutableList.of(couponState.getInitiatingParty().getOwningKey(), couponState.getCounterParty().getOwningKey()));

            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addOutputState(couponState, CouponContract.COUPON_CONTRACT_ID)
                    .addCommand(couponGenerationCommand);

            txBuilder.verify(getServiceHub());

            /*progressTracker.setCurrentStep(SIGNING_TRANSACTION);*/
            getStateMachine().getLogger().info("SIGNING_TRANSACTION");

            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            //Stage 4
            /*progressTracker.setCurrentStep(GATHERING_SIGS);*/

            getStateMachine().getLogger().info("GATHERING SIGNATURES FROM BOTH PARTIES");

            // Send the state to the counterparty, and receive it back with their signature.
            FlowSession otherPartySession = initiateFlow(couponVendorParty);

            final SignedTransaction fullySignedTx = subFlow(new CollectSignaturesFlow(partSignedTx, ImmutableSet.of(otherPartySession), CollectSignaturesFlow.Companion.tracker()));
            //stage 5

            getStateMachine().getLogger().info("FINALISING TRANSACTION  : " + fullySignedTx);
            /*progressTracker.setCurrentStep(FINALISING_TRANSACTION);*/

            getStateMachine().getLogger().info("FINALISING_TRANSACTION :");

            getStateMachine().getLogger().info("Notorize and record transaction in both party vaults");
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

            class SignTxFlow extends SignTransactionFlow {

                public SignTxFlow(FlowSession otherSideSession, ProgressTracker progressTracker) {
                    super(otherSideSession, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) throws FlowException {
                    requireThat(require -> {
                        ContractState output = stx.getTx().getOutputs().get(0).getData();
                        require.using("This must be a transaction between coupon issuer  and coupon vendor (CouponState transaction).", output instanceof CouponState);
                        return null;
                    });
                }
            }
            return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
        }
    }
}
