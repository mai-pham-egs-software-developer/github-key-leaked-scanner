package com.leakscanner.crypto.dto;

import com.leakscanner.crypto.ChainEco;

import java.math.BigDecimal;


public class BalanceResultDto {
    private ChainEco chain;
    private String pk;
    private String address;
    private BigDecimal balance;
    private BigDecimal value; // USD amount converted

    public ChainEco getChain() {
        return chain;
    }

    public void setChain(ChainEco chain) {
        this.chain = chain;
    }

    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }
}
