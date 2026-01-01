package com.bank.debit.exception;

/**
 * Exception thrown when a customer does not exist
 */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(String customerId) {
        super("Customer not found with id: " + customerId);
    }
}
