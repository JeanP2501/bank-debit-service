package com.bank.debit.service;

import com.bank.debit.client.AccountClient;
import com.bank.debit.client.CustomerClient;
import com.bank.debit.exception.BusinessRuleException;
import com.bank.debit.exception.DebitException;
import com.bank.debit.mapper.DebitMapper;
import com.bank.debit.model.AssociateAccountRequest;
import com.bank.debit.model.CreateDebitCardRequest;
import com.bank.debit.model.DebitCardResponse;
import com.bank.debit.model.entity.Debit;
import com.bank.debit.repository.DebitRepository;
import com.bank.debit.validator.DebitValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
                    return debitRepository.save(entity);
                })
                .map(debitMapper::toResponse);
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

    private Mono<Debit> getActiveDebitCard(String customerId) {
        log.debug("Find active debit card: {}", customerId);

        return debitRepository.findByCustomerIdAndActiveTrue(customerId)
                .switchIfEmpty(Mono.error(new DebitException(
                        "No active debit card found for customer: " + customerId)))
                .doOnSuccess(card -> log.debug("Debit card found - CardId: {}", card.getId()));
    }

}
