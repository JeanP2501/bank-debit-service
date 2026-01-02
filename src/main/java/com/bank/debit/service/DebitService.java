package com.bank.debit.service;

import com.bank.debit.client.AccountClient;
import com.bank.debit.client.CustomerClient;
import com.bank.debit.client.TransactionClient;
import com.bank.debit.exception.BusinessRuleException;
import com.bank.debit.exception.DebitException;
import com.bank.debit.exception.InsufficientFundsException;
import com.bank.debit.mapper.DebitMapper;
import com.bank.debit.model.*;
import com.bank.debit.model.dto.TransactionResponse;
import com.bank.debit.model.entity.Debit;
import com.bank.debit.repository.DebitRepository;
import com.bank.debit.validator.DebitValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DebitService {

    private final AccountClient accountClient;
    private final CustomerClient customerClient;
    private final DebitRepository debitRepository;
    private final DebitValidator debitValidator;
    private final DebitMapper debitMapper;
    private final TransactionClient transactionClient;

    public Mono<DebitCardResponse> createDebitCard(CreateDebitCardRequest request) {

        log.info("Iniciando creación de tarjeta de débito - CustomerId: {}, AccountId: {}",
                request.getCustomerId(), request.getPrimaryAccountId());

        return debitValidator.validateCustomerIsActive(request.getCustomerId())
                .then(debitValidator.validateAccountIsActive(request.getPrimaryAccountId()))
                .then(debitValidator.validateDebitCardNotExists(request.getCustomerId(), request.getPrimaryAccountId()))
                .then(createAndSaveDebitCard(request))
                .doOnSuccess(response -> log.info("Created debit card - CardId: {}", response.getId()))
                .doOnError(error -> log.error("Error to try create debit card: {}", error.getMessage()));
    }

    private Mono<DebitCardResponse> createAndSaveDebitCard(CreateDebitCardRequest request) {
        log.debug("Create new debit card");

        return Mono.fromCallable(() -> debitMapper.toEntity(request))
                .flatMap(entity -> {
                    entity.setAssociatedAccounts(new ArrayList<>());
                    entity.getAssociatedAccounts().add(request.getPrimaryAccountId());
                    entity.setCardNumber(generateCardNumber());
                    return debitRepository.save(entity);
                })
                .map(debitMapper::toResponse);
    }

    private String generateCardNumber() {
        String number = String.format("%016d", (long)(Math.random() * 10000000000000000L));
        return maskCardNumber(number);
    }

    private String maskCardNumber(String cardNumber) {
        if (cardNumber.length() == 16) {
            return "****-****-****-" + cardNumber.substring(12);
        }
        return cardNumber;
    }

    public Mono<DebitCardResponse> associateAccount(AssociateAccountRequest request) {

        log.info("Associate new account to debit card - CustomerId: {}, AccountId: {}",
                request.getCustomerId(), request.getAccountId());

        return getActiveDebitCard(request.getCustomerId())
                .flatMap(debitCard -> debitValidator.validateAndAssociateAccount(debitCard, request.getAccountId()))
                .flatMap(debitRepository::save)
                .map(debitMapper::toResponse)
                .doOnSuccess(response -> log.info("Account associated - CardId: {}, AccountId: {}",
                        response.getId(), request.getAccountId()))
                .doOnError(error -> log.error("Error to associate account: {}", error.getMessage()));
    }

    public Mono<Debit> getActiveDebitCard(String customerId) {
        log.debug("Find active debit card: {}", customerId);

        return debitRepository.findByCustomerIdAndActiveTrue(customerId)
                .switchIfEmpty(Mono.error(new DebitException(
                        "No active debit card found for customer: " + customerId)))
                .doOnSuccess(card -> log.debug("Debit card found - CardId: {}", card.getId()));
    }

    // Transaction
    public Mono<DebitTransactionResponse> processTransaction(DebitTransactionRequest request) {

        log.info("Iniciando transacción - DebitCardId: {}, Amount: {}",
                request.getDebitCardId(), request.getAmount());

        return validateTransactionAmount(request.getAmount())
                .then(getActiveDebitCardById(request.getDebitCardId()))
                .flatMap(debitCard -> processWithdrawalWithFallback(debitCard, request))
                .doOnSuccess(response -> log.info("Transacción completada exitosamente - TransactionId: {}",
                        response.getTransactionId()))
                .doOnError(error -> log.error("Error al procesar transacción: {}", error.getMessage()));
    }

    private Mono<Void> validateTransactionAmount(Double amount) {
        if (amount == null || amount <= 0) {
            return Mono.error(new BusinessRuleException("Amount must be greater than 0"));
        }
        return Mono.empty();
    }

    private Mono<Debit> getActiveDebitCardById(String debitCardId) {
        log.debug("Buscando tarjeta de débito activa - CardId: {}", debitCardId);

        return debitRepository.findById(debitCardId)
                .switchIfEmpty(Mono.error(new DebitException("Debit card not found: " + debitCardId)))
                .flatMap(debitCard -> {
                    if (!debitCard.isActive()) {
                        return Mono.error(new BusinessRuleException("Debit card is not active: " + debitCardId));
                    }
                    log.debug("Tarjeta de débito activa encontrada - CardId: {}", debitCardId);
                    return Mono.just(debitCard);
                });
    }

    private Mono<DebitTransactionResponse> processWithdrawalWithFallback(
            Debit debitCard, DebitTransactionRequest request) {

        log.info("Procesando retiro con {} cuentas asociadas", debitCard.getAssociatedAccounts().size());

        return tryWithdrawalOnAccounts(
                debitCard.getAssociatedAccounts(),
                0,
                BigDecimal.valueOf(request.getAmount()),
                request.getDescription(),
                debitCard.getId()
        );
    }

    private Mono<DebitTransactionResponse> tryWithdrawalOnAccounts(
            List<String> accounts,
            int index,
            BigDecimal amount,
            String description,
            String debitCardId) {

        if (index >= accounts.size()) {
            log.error("Fondos insuficientes en todas las {} cuentas asociadas", accounts.size());
            return Mono.error(new InsufficientFundsException(
                    String.format("Insufficient funds in all %d associated accounts", accounts.size())));
        }

        String currentAccountId = accounts.get(index);
        log.info("Intentando retiro en cuenta {} ({}/{})", currentAccountId, index + 1, accounts.size());

        return transactionClient.processWithdrawal(currentAccountId, amount, description)
                .map(transactionResponse -> {
                    log.info("Retiro exitoso en cuenta {} - TransactionId: {} - Status: {}",
                            currentAccountId, transactionResponse.getId(), transactionResponse.getStatus());
                    return mapToDebitTransactionResponse(
                            transactionResponse, debitCardId, currentAccountId, amount, description);
                })
                .onErrorResume(InsufficientFundsException.class, error -> {
                    log.warn("Fondos insuficientes en cuenta {} ({}/{}): {}",
                            currentAccountId, index + 1, accounts.size(), error.getMessage());
                    log.info("Intentando con la siguiente cuenta asociada...");

                    // Intenta con la siguiente cuenta
                    return tryWithdrawalOnAccounts(accounts, index + 1, amount, description, debitCardId);
                })
                .onErrorResume(error -> {
                    // Cualquier otro error se propaga inmediatamente
                    log.error("Error no recuperable en cuenta {}: {}", currentAccountId, error.getMessage());
                    return Mono.error(error);
                });
    }

    private boolean isInsufficientFundsError(Throwable error) {
        boolean isInsufficientFunds = error instanceof InsufficientFundsException ||
                (error.getMessage() != null &&
                        (error.getMessage().contains("Insufficient funds") ||
                                error.getMessage().contains("insufficient balance") ||
                                error.getMessage().contains("Fondos insuficientes")));

        log.debug("Error type: {}, Is insufficient funds: {}, Message: {}",
                error.getClass().getSimpleName(), isInsufficientFunds, error.getMessage());

        return isInsufficientFunds;
    }

    private DebitTransactionResponse mapToDebitTransactionResponse(
            TransactionResponse transactionResponse,
            String debitCardId,
            String accountId,
            BigDecimal amount,
            String description) {

        DebitTransactionResponse response = new DebitTransactionResponse();
        response.setTransactionId(transactionResponse.getId());
        response.setDebitCardId(debitCardId);
        response.setAccountId(accountId);
        response.setAmount(amount.doubleValue());
        response.setDescription(description);
        response.setTimestamp(transactionResponse.getCreatedAt().atOffset(ZoneOffset.UTC));
        response.setStatus(transactionResponse.getStatus().toString());

        return response;
    }

    public Mono<DebitCardResponse> getDebitCardById(String id) {
        log.info("Consultando tarjeta de débito por ID: {}", id);

        return debitRepository.findById(id)
                .switchIfEmpty(Mono.error(new DebitException("Debit card not found: " + id)))
                .map(debitMapper::toResponse)
                .doOnSuccess(response -> log.info("Tarjeta de débito encontrada - CardId: {}, CustomerId: {}",
                        response.getId(), response.getCustomerId()))
                .doOnError(error -> log.error("Error al buscar tarjeta de débito {}: {}",
                        id, error.getMessage()));
    }

    public Mono<DebitCardResponse> getDebitCardByCustomerId(String customerId) {
        log.info("Consultando tarjeta de débito activa por CustomerId: {}", customerId);

        return debitRepository.findByCustomerId(customerId)
                .switchIfEmpty(Mono.error(new DebitException(
                        "No active debit card found for customer: " + customerId)))
                .map(debitMapper::toResponse)
                .doOnSuccess(response -> log.info("Tarjeta de débito activa encontrada - CardId: {}, CustomerId: {}",
                        response.getId(), response.getCustomerId()))
                .doOnError(error -> log.error("Error al buscar tarjeta de débito para customer {}: {}",
                        customerId, error.getMessage()));
    }

}
