package com.bank.debit.model.enums;

/**
 * Transaction type enumeration
 * Defines the types of transactions in the banking system
 */
public enum TransactionType {
    /**
     * Deposit to bank account
     */
    DEPOSIT,

    /**
     * Withdrawal from bank account
     */
    WITHDRAWAL,

    /**
     * Payment to credit product
     */
    PAYMENT,

    /**
     * Charge to credit card
     */
    CHARGE,
    /**
     * Intendify addition or substraction of a Transfer
     */
    TRANSFER
}
