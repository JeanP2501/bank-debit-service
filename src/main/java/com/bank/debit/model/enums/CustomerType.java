package com.bank.debit.model.enums;

/**
 * Customer type enumeration
 * Used for business rule validations
 */
public enum CustomerType {
    /**
     * Personal customer - Individual person.
     */
    PERSONAL,

    /**
     * Personal VIP customer - Individual with special benefits.
     * Requires an active credit card to open accounts
     */
    PERSONAL_VIP,

    /**
     * Business customer - Company or organization.
     */
    BUSINESS,

    /**
     * Business PYME customer - Small and medium enterprises.
     * Requires an active credit card to open accounts
     */
    BUSINESS_PYME;

    /**
     * Check if customer type requires credit card.
     * @return true if VIP or PYME
     */
    public boolean requiresCreditCard() {
        return this == PERSONAL_VIP || this == BUSINESS_PYME;
    }

    /**
     * Check if customer is personal type (including VIP).
     * @return true if PERSONAL or PERSONAL_VIP
     */
    public boolean isPersonal() {
        return this == PERSONAL || this == PERSONAL_VIP;
    }

    /**
     * Check if customer is business type (including PYME).
     * @return true if BUSINESS or BUSINESS_PYME
     */
    public boolean isBusiness() {
        return this == BUSINESS || this == BUSINESS_PYME;
    }
}
