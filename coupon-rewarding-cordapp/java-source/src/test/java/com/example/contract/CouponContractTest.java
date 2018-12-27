
package com.example.contract;

import com.example.state.CouponState;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.core.TestIdentity;
import net.corda.testing.node.MockServices;
import org.junit.Test;
import static com.example.contract.CouponContract.COUPON_CONTRACT_ID;
import static net.corda.testing.node.NodeTestUtils.ledger;
import static net.corda.testing.node.NodeTestUtils.transaction;

public class CouponContractTest {

    static private final MockServices ledgerServices = new MockServices();
    static private TestIdentity netmeds = new TestIdentity(new CordaX500Name("netMeds", "london", "GB"));
    static private TestIdentity amazon = new TestIdentity(new CordaX500Name("amazon", "New York", "US"));

    private static int amount = 500;
    private static String couponName = "Diwali Dhamaka";

    private static CouponState couponState = new CouponState(netmeds.getParty(), amazon.getParty(), amount, new UniqueIdentifier(), false, true, "ShivanSawant", couponName);

    @Test
    public void transactionMustIncludeCreateCommand() {

        ledger(ledgerServices, (ledger -> {
            ledger.transaction(tx -> {
                tx.output(COUPON_CONTRACT_ID, couponState);
                tx.fails();
                tx.command(ImmutableList.of(netmeds.getParty().getOwningKey(), amazon.getParty().getOwningKey()), new CouponContract.Commands.CouponGeneration());
                tx.verifies();
                return null;
            });
            return null;
        }));
    }

    @Test
    public void transactionMustHaveNoInputs() {

        transaction(ledgerServices,tx -> {
            tx.output(COUPON_CONTRACT_ID, couponState);
            tx.command(ImmutableList.of(netmeds.getParty().getOwningKey(), amazon.getParty().getOwningKey()), new CouponContract.Commands.CouponGeneration());
            tx.verifies();
            return null;
        });


/**** uncomment for failure criteria **//*

           */
/* transaction(ledgerServices,tx -> {
                tx.input(FINANCE_CONTRACT_ID, financeBankState);
                tx.output(FINANCE_CONTRACT_ID, financeBankState);
                tx.command(ImmutableList.of(finance.getParty().getOwningKey(), bank.getParty().getOwningKey()), new LoanReqContract.Commands.InitiateLoan());
                tx.failsWith("No inputs should be consumed when issuing .");
                return null;
            });*/

    }

    @Test
    public void transactionMustHaveOneOutput() {

        transaction(ledgerServices,tx -> {
            tx.output(COUPON_CONTRACT_ID, couponState);
            tx.command(ImmutableList.of(netmeds.getPublicKey(), amazon.getPublicKey()), new CouponContract.Commands.CouponGeneration());
            tx.verifies();
            return null;
        });


/**** uncomment for failure criteria **//*

        */
/* transaction(ledgerServices,tx -> {
            tx.output(FINANCE_CONTRACT_ID, financeBankState);
            tx.output(FINANCE_CONTRACT_ID, financeBankState);
                tx.command(ImmutableList.of(finance.getPublicKey(), bank.getPublicKey()), new LoanReqContract.Commands.InitiateLoan());
                tx.failsWith("Only one output state should be created.");
                return null;
            });*/

    }

    @Test
    public void lenderMustSignTransaction() {

        transaction(ledgerServices,tx -> {
            tx.output(COUPON_CONTRACT_ID, couponState);
            tx.command(ImmutableList.of(netmeds.getPublicKey(),amazon.getPublicKey()), new CouponContract.Commands.CouponGeneration());
            tx.verifies();
            return null;
        });


/****uncomment for failure criteria **//*

       */
/* transaction(ledgerServices,tx -> {
                tx.output(FINANCE_CONTRACT_ID, new LoanRequestState(finance.getParty(), bank.getParty(), companyName,amount,new UniqueIdentifier(),false,new UniqueIdentifier()));
                tx.command(ImmutableList.of(bank.getPublicKey()), new LoanReqContract.Commands.InitiateLoan());
                tx.failsWith("All of the participants must be signers.");
                return null;
            });*/

    }

    @Test
    public void borrowerMustSignTransaction() {

        transaction(ledgerServices,tx -> {
            tx.output(COUPON_CONTRACT_ID, couponState);
            tx.command(ImmutableList.of(netmeds.getPublicKey(),amazon.getPublicKey()), new CouponContract.Commands.CouponGeneration());
            tx.verifies();
            return null;
        });

/**** uncomment for failure criteria ****//*

         */
/*transaction(ledgerServices,tx -> {
                tx.output(FINANCE_CONTRACT_ID, new LoanRequestState(finance.getParty(), bank.getParty(), companyName,amount,new UniqueIdentifier(),false,new UniqueIdentifier()));
                tx.command(ImmutableList.of(finance.getPublicKey(),bank.getPublicKey()), new LoanReqContract.Commands.InitiateLoan());
                tx.failsWith("All of the participants must be signers.");
                return null;
        });*/

    }

    @Test
    public void lenderIsNotBorrower() {

        transaction(ledgerServices,tx -> {
            tx.output(COUPON_CONTRACT_ID, couponState);
            tx.command(ImmutableList.of(netmeds.getPublicKey(),amazon.getPublicKey()), new CouponContract.Commands.CouponGeneration());
            tx.verifies();
            return null;
        });
    }


    @Test
    public void couponStateMustHaveOneInputs() {

/**** This test case also checks the both parties actually sign the transaction ***/

        transaction(ledgerServices, tx -> {
            tx.input(COUPON_CONTRACT_ID, couponState);
            tx.output(COUPON_CONTRACT_ID, couponState);
            tx.command(ImmutableList.of(amazon.getParty().getOwningKey(), netmeds.getParty().getOwningKey()), new CouponContract.Commands.CouponVerification());
            tx.verifies();
            return null;
        });
    }


    @Test
    public void redemptionCouponStateMustHaveOneInputs() {

        transaction(ledgerServices, tx -> {
            tx.input(COUPON_CONTRACT_ID, couponState);
            tx.output(COUPON_CONTRACT_ID, couponState);
            tx.command(ImmutableList.of(amazon.getParty().getOwningKey(), netmeds.getParty().getOwningKey()), new CouponContract.Commands.CouponRedemption());
            return null;
        });
    }

    @Test
    public void verificationCouponStateMustHaveOneOuputs() {

/**** This test case also checks the both parties actually sign the transaction ***/

        transaction(ledgerServices, tx -> {
            tx.input(COUPON_CONTRACT_ID, couponState);
            tx.output(COUPON_CONTRACT_ID, couponState);
            tx.command(ImmutableList.of(amazon.getParty().getOwningKey(), netmeds.getParty().getOwningKey()), new CouponContract.Commands.CouponVerification());
            tx.verifies();
            return null;
        });
    }

    @Test
    public void redemptionCouponStateMustHaveOneOutputs() {

        transaction(ledgerServices, tx -> {
            tx.input(COUPON_CONTRACT_ID, couponState);
            tx.output(COUPON_CONTRACT_ID, couponState);
            tx.command(ImmutableList.of(amazon.getParty().getOwningKey(), netmeds.getParty().getOwningKey()), new CouponContract.Commands.CouponRedemption());
            return null;
        });
    }

}

