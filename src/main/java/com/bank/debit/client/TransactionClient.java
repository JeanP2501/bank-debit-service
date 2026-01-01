package com.bank.debit.client;

import com.bank.debit.exception.InsufficientFundsException;
import com.bank.debit.model.dto.TransactionResponse;
import com.bank.debit.model.dto.WithdrawalRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Slf4j
@Component
public class TransactionClient {

    private final WebClient webClient;

    public TransactionClient(WebClient.Builder webClientBuilder,
                             @Value("${transaction.service.url}") String customerServiceUrl) {
        this.webClient = webClientBuilder
                .baseUrl(customerServiceUrl)
                .build();
    }

    public Mono<TransactionResponse> processWithdrawal(String accountId, BigDecimal amount, String description) {
        log.info("Llamando al servicio de transacciones - AccountId: {}, Amount: {}", accountId, amount);

        WithdrawalRequest request = WithdrawalRequest.builder()
                .accountId(accountId)
                .amount(amount)
                .description(description)
                .build();

        return webClient.post()
                .uri("/api/transactions/withdrawal")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(TransactionResponse.class)
                .flatMap(response -> {
                    // Verificar si la transacción falló
                    if ("FAILED".equalsIgnoreCase(response.getStatus().toString())) {
                        log.warn("Transacción fallida - Account: {}, Status: {}, Error: {}",
                                accountId, response.getStatus(), response.getErrorMessage());

                        // Verificar si es por fondos insuficientes
                        if (response.getErrorMessage() != null &&
                                response.getErrorMessage().contains("Insufficient funds")) {
                            return Mono.error(new InsufficientFundsException(response.getErrorMessage()));
                        }

                        // Otro tipo de error
                        return Mono.error(new RuntimeException(
                                "Transaction failed: " + response.getErrorMessage()));
                    }

                    // Transacción exitosa
                    log.info("Transacción exitosa - TransactionId: {}, Status: {}",
                            response.getId(), response.getStatus());
                    return Mono.just(response);
                })
                .doOnError(error -> {
                    if (!(error instanceof InsufficientFundsException)) {
                        log.error("Error inesperado en transacción para account {}: {}",
                                accountId, error.getMessage());
                    }
                });
    }

}
