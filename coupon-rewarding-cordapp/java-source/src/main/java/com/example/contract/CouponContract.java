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
            verifyCouponGeneration(tx, requiredSigners);
        } else if (commandType instanceof Commands.CouponVerification) {
            verifyCouponUsage(tx, requiredSigners);
        }
    }

    private void verifyCouponGeneration(LedgerTransaction tx, List<PublicKey> signers) {
        requireThat(req -> {

            req.using("No inputs should be consumed while generating a coupon", tx.getInputStates().isEmpty());
            req.using("Only one output should be created during the process of generating coupon", tx.getOutputStates().size() == 1);

            ContractState outputState = tx.getOutput(0);
            req.using("Output must be of the tyep CouponState ", outputState instanceof CouponState);
            CouponState couponState = (CouponState) outputState;
            req.using("Purchasing amount should be more than or equal to 20000", couponState.getAmount() >= 2000);

            Party netmeds =  couponState.getInitiatingParty();
            PublicKey netmedsKey  = netmeds.getOwningKey();
            PublicKey amazonKey = couponState.getCounterParty().getOwningKey();

            req.using("Netmeds should sign the transaction ", signers.contains(netmedsKey));
            req.using("Amazon should sign the transaction", signers.contains(amazonKey));

            return  null;
        });
    }

    private void verifyCouponUsage(LedgerTransaction tx, List<PublicKey> signers) {

        requireThat(req -> {

            req.using("Only one input should be consumed while verification of coupon", tx.getInputStates().size() == 1);
            req.using("Only one output should be created ", tx.getOutputStates().size() == 1);

            ContractState input = tx.getInput(0);
            ContractState output = tx.getOutput(0);

            req.using("input must be of the type CouponState ", input instanceof CouponState);
            req.using("Output must be of the type CouponState ", output instanceof CouponState);

            CouponState inputState = (CouponState) input;
            CouponState outputState = (CouponState) output;

            PublicKey netmedsKey = inputState.getInitiatingParty().getOwningKey();
            PublicKey amazonkey = outputState.getCounterParty().getOwningKey();

            req.using("Netmeds should sign the transaction", signers.contains(netmedsKey));
            req.using("Amazon should sign the transaction", signers.contains(amazonkey));

            return null;
        });

    }

    public interface Commands extends CommandData {
        public class CouponGeneration implements Commands { }
        public class CouponVerification implements Commands { }
    }
}
