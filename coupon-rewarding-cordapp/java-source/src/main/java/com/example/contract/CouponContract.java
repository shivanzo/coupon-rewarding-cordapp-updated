package com.example.contract;

import com.example.state.CouponState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.Party;
import net.corda.core.transactions.LedgerTransaction;
import org.jetbrains.annotations.NotNull;

import java.security.PublicKey;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class CouponContract implements Contract {

    public static final String COUPON_CONTRACT_ID = "com.example.contract.CouponContract";

    @Override
    public void verify(@NotNull LedgerTransaction tx) throws IllegalArgumentException {

        if (tx != null && tx.getCommands().size() != 1)
            throw new IllegalArgumentException("Transaction must have one command");

        Command command = tx.getCommand(0);
        List<PublicKey> requiredSigners = command.getSigners();
        CommandData commandType = command.getValue();

        if (commandType instanceof  Commands.CouponGeneration) {
            verifyCouponIssuance(tx, requiredSigners);
        } else if (commandType instanceof Commands.CouponVerification) {
            verifyCouponAcceptance(tx, requiredSigners);
        } else if (commandType instanceof Commands.CouponRedemption) {
            verifyCouponRedemption(tx, requiredSigners);
        }
    }

    private void verifyCouponIssuance(LedgerTransaction tx, List<PublicKey> signers) {
        requireThat(req -> {

            req.using("No inputs should be consumed while generating a coupon", tx.getInputStates().isEmpty());
            req.using("Only one output should be created during the process of generating coupon", tx.getOutputStates().size() == 1);

            ContractState outputState = tx.getOutput(0);
            req.using("Output must be of the type CouponState ", outputState instanceof CouponState);
            CouponState couponState = (CouponState) outputState;

            req.using("Purchasing amount should be more than or equal to 500", couponState.getAmount() >= 500);
            req.using("User name/email to whom the coupon is assigned, should not be empty ", !(couponState.getUsername().isEmpty()));
            req.using("Merchant name cannot be empty ", couponState.getCounterParty() != null);

            Party couponIssuanceParty =  couponState.getInitiatingParty();
            PublicKey couponIssuancePartyKey  = couponIssuanceParty.getOwningKey();
            PublicKey vendorKey = couponState.getCounterParty().getOwningKey();

            req.using("Coupon issuer, Netmeds should sign the transaction ", signers.contains(couponIssuancePartyKey));
            req.using("Coupon vendor should sign the transaction", signers.contains(vendorKey));

            return  null;
        });
    }

    private void verifyCouponAcceptance(LedgerTransaction tx, List<PublicKey> signers) {

        requireThat(req -> {

            req.using("Only one input should be consumed while verification of coupon", tx.getInputStates().size() == 1);
            req.using("Only one output should be created ", tx.getOutputStates().size() == 1);

            ContractState input = tx.getInput(0);
            ContractState output = tx.getOutput(0);

            req.using("Input must be of the type CouponState ", input instanceof CouponState);
            req.using("Output must be of the type CouponState ", output instanceof CouponState);

            CouponState inputState = (CouponState) input;
            CouponState outputState = (CouponState) output;

            PublicKey couponIssuerKey = inputState.getInitiatingParty().getOwningKey();
            PublicKey vendorKey = outputState.getCounterParty().getOwningKey();

            req.using("Coupon-Isssuer should sign the transaction", signers.contains(couponIssuerKey));
            req.using("Coupon-Vendor should sign the transaction", signers.contains(vendorKey));

            return null;
        });
    }

    private void verifyCouponRedemption(LedgerTransaction tx, List<PublicKey> signers) {

        requireThat(req -> {

            req.using("Only one input should be consumed while redemption of the coupon", tx.getInputStates().size() == 1);
            req.using("Only one output should be created while redemption of the coupon ", tx.getOutputStates().size() == 1);

            ContractState input = tx.getInput(0);
            ContractState output = tx.getOutput(0);

            req.using("input must be of the type CouponState ", input instanceof CouponState);
            req.using("Output must be of the type CouponState ", output instanceof CouponState);

            CouponState inputState = (CouponState) input;
            CouponState outputState = (CouponState) output;

            req.using("coupon should be authorized by vendor " , (inputState.isCouponApproved()));

            PublicKey couponIssuerKey = inputState.getInitiatingParty().getOwningKey();
            PublicKey vendorKey = outputState.getCounterParty().getOwningKey();

            req.using("CouponIsssuer should sign the transaction", signers.contains(couponIssuerKey));
            req.using("Coupon Vendor should sign the transaction", signers.contains(vendorKey));

            return null;
            });
    }

    public interface Commands extends CommandData {
        public class CouponGeneration implements Commands { }
        public class CouponVerification implements Commands { }
        public class CouponRedemption implements Commands { }
    }
}
