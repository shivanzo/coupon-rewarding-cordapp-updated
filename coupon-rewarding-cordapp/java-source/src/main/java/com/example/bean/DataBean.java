package com.example.bean;

import net.corda.core.identity.CordaX500Name;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class DataBean {

    private int value;
    private CordaX500Name partyName;
    private String couponId;

    public int getValue() {
        return value;
    }

    public CordaX500Name getPartyName() {
        return partyName;
    }

    public String getCouponId() {
        return couponId;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public void setPartyName(CordaX500Name partyName) {
        this.partyName = partyName;
    }

    public void setCouponId(String couponId) {
        this.couponId = couponId;
    }

    @Override
    public String toString() {
        return "DataBean{" +
                "value=" + value +
                ", partyName=" + partyName +
                ", couponId='" + couponId + '\'' +
                '}';
    }
}
