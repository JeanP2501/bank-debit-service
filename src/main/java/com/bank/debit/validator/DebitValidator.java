package com.bank.debit.validator;

import com.bank.debit.client.AccountClient;
import com.bank.debit.client.CustomerClient;
import com.bank.debit.exception.BusinessRuleException;
import com.bank.debit.exception.DebitException;
import com.bank.debit.model.entity.Debit;
import com.bank.debit.repository.DebitRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class DebitValidator {

  private final AccountClient accountClient;
  private final CustomerClient customerClient;
  private final DebitRepository debitRepository;

  public Mono<Void> validateCustomerIsActive(String customerId) {
    log.debug("Validando customer activo: {}", customerId);

    return customerClient
        .getCustomerById(customerId)
        .switchIfEmpty(Mono.error(new DebitException("Customer not found: " + customerId)))
        .flatMap(
            customer -> {
              if (!customer.isActive()) {
                return Mono.error(new BusinessRuleException("Customer is inactive: " + customerId));
              }
              log.debug("Customer {} está activo", customerId);
              return Mono.empty();
            });
  }

  public Mono<Void> validateAccountIsActive(String accountId) {
    log.debug("Validando account activa: {}", accountId);

    return accountClient
        .getAccount(accountId)
        .switchIfEmpty(Mono.error(new DebitException("Account not found: " + accountId)))
        .flatMap(
            account -> {
              if (!account.getActive()) {
                return Mono.error(new BusinessRuleException("Account is inactive: " + accountId));
              }
              log.debug("Account {} está activa", accountId);
              return Mono.empty();
            });
  }

  public Mono<Void> validateDebitCardNotExists(String customerId, String accountId) {
    log.debug(
        "Validando que no exista tarjeta de débito para customer: {} y account: {}",
        customerId,
        accountId);

    return debitRepository
        .findByCustomerIdAndPrimaryAccountId(customerId, accountId)
        .flatMap(
            existingCard -> {
              log.warn(
                  "Ya existe una tarjeta de débito para customer: {} y account: {}",
                  customerId,
                  accountId);
              return Mono.<Void>error(
                  new BusinessRuleException("Customer already has a debit card for this account"));
            })
        .then();
  }

  public Mono<Debit> validateAndAssociateAccount(Debit debitCard, String accountId) {
    log.debug("Validando cuenta {} para asociar a tarjeta {}", accountId, debitCard.getId());

    return validateAccountIsActive(accountId)
        .then(validateAccountNotAlreadyAssociated(debitCard, accountId))
        .then(
            Mono.fromCallable(
                () -> {
                  debitCard.getAssociatedAccounts().add(accountId);
                  debitCard.setUpdatedAt(LocalDateTime.now());
                  log.debug("Cuenta {} agregada a la lista de cuentas asociadas", accountId);
                  return debitCard;
                }));
  }

  public Mono<Void> validateAccountNotAlreadyAssociated(Debit debitCard, String accountId) {
    if (debitCard.getAssociatedAccounts().contains(accountId)) {
      log.warn("La cuenta {} ya está asociada a la tarjeta {}", accountId, debitCard.getId());
      return Mono.error(
          new BusinessRuleException(
              "Account " + accountId + " is already associated with this debit card"));
    }
    return Mono.empty();
  }
}
