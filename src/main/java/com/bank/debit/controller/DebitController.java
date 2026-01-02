package com.bank.debit.controller;

import com.bank.debit.api.DebitCardsApi;
import com.bank.debit.model.*;
import com.bank.debit.service.DebitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequiredArgsConstructor
public class DebitController implements DebitCardsApi {

    private final DebitService debitService;

    @Override
    public Mono<ResponseEntity<DebitCardResponse>> createDebitCard(
            Mono<CreateDebitCardRequest> createDebitCardRequest,
            ServerWebExchange exchange) {

        log.info("Recibiendo solicitud para crear tarjeta de débito");

        return createDebitCardRequest
                .doOnNext(request ->
                        log.info("Request recibido - CustomerId: {}, AccountId: {}",
                        request.getCustomerId(), request.getPrimaryAccountId()))
                .flatMap(debitService::createDebitCard)
                .map(response -> {
                    log.info("Tarjeta de débito creada exitosamente - CardId: {}",
                            response.getId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .doOnError(error -> log.error("Error al crear tarjeta de débito: {}",
                        error.getMessage()));
    }

    @Override
    public Mono<ResponseEntity<DebitCardResponse>> associateAccountToDebit(
            Mono<AssociateAccountRequest> associateAccountRequest,
            ServerWebExchange exchange) {

        log.info("Recibiendo solicitud para asociar cuenta a tarjeta de débito");

        return associateAccountRequest
                .doOnNext(request ->
                        log.info("Request recibido - CustomerId: {}, AccountId: {}",
                        request.getCustomerId(), request.getAccountId()))
                .flatMap(debitService::associateAccount)
                .map(response -> {
                    log.info("Cuenta asociada exitosamente a la tarjeta - CardId: {}",
                            response.getId());
                    return ResponseEntity.ok(response);
                })
                .doOnError(error -> log.error("Error al asociar cuenta: {}",
                        error.getMessage()));
    }

    @Override
    public Mono<ResponseEntity<DebitTransactionResponse>> processDebitTransaction(
            Mono<DebitTransactionRequest> debitTransactionRequest,
            ServerWebExchange exchange) {

        log.info("Recibiendo solicitud para procesar transacción con tarjeta de débito");

        return debitTransactionRequest
                .doOnNext(request -> log.info("Request recibido - DebitCardId: {}, Amount: {}",
                        request.getDebitCardId(), request.getAmount()))
                .flatMap(debitService::processTransaction)
                .map(response -> {
                    log.info("Transacción procesada exitosamente - TransactionId: {}",
                            response.getTransactionId());
                    return ResponseEntity.status(HttpStatus.CREATED).body(response);
                })
                .doOnError(error -> log.error("Error al procesar transacción: {}", error.getMessage()));
    }

    @Override
    public Mono<ResponseEntity<DebitCardResponse>> getDebitCardById(
            String id,
            ServerWebExchange exchange) {

        log.info("Recibiendo solicitud para consultar tarjeta de débito - ID: {}", id);

        return debitService.getDebitCardById(id)
                .map(response -> {
                    log.info("Tarjeta de débito encontrada - CardId: {}", response.getId());
                    return ResponseEntity.ok(response);
                })
                .doOnError(error -> log.error("Error al consultar tarjeta de débito {}: {}",
                        id, error.getMessage()));
    }

    @Override
    public Mono<ResponseEntity<DebitCardResponse>> getDebitCardByCustomerId(
            String customerId,
            ServerWebExchange exchange) {

        log.info("Recibiendo solicitud para consultar tarjeta de débito por CustomerId: {}", customerId);

        return debitService.getDebitCardByCustomerId(customerId)
                .map(response -> {
                    log.info("Tarjeta de débito encontrada para customer - CardId: {}, CustomerId: {}",
                            response.getId(), response.getCustomerId());
                    return ResponseEntity.ok(response);
                })
                .doOnError(error -> log.error("Error al consultar tarjeta de débito para customer {}: {}",
                        customerId, error.getMessage()));
    }

}
