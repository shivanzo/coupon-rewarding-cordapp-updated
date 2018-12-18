package com.example.state;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.serialization.ConstructorForDeserialization;
import net.corda.core.serialization.CordaSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@CordaSerializable
public class CouponState implements LinearState {

   private Party initiatingParty;
   private Party counterParty;
   private int amount;
   private boolean isCouponUtilized;
   private UniqueIdentifier couponId;

   @ConstructorForDeserialization
   public CouponState(Party initiatingParty, Party counterParty, int amount, UniqueIdentifier couponId, boolean isCouponUtilized) {
       this.initiatingParty = initiatingParty;
       this.counterParty = counterParty;
       this.amount = amount;
       this.couponId = couponId;
       this.isCouponUtilized = isCouponUtilized;
   }

    public Party getInitiatingParty() {
        return initiatingParty;
    }

    public Party getCounterParty() {
        return counterParty;
    }

    public int getAmount() {
        return amount;
    }

    public boolean isCouponUtilized() {
        return isCouponUtilized;
    }

    public UniqueIdentifier getCouponId() {
        return couponId;
    }

    public void setCounterParty(Party counterParty) {
        this.counterParty = counterParty;
    }

    public void setCouponUtilized(boolean couponUtilized) {
        isCouponUtilized = couponUtilized;
    }

    @NotNull
    @Override
    public UniqueIdentifier getLinearId() {
        return couponId;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(initiatingParty, counterParty);
    }
}
