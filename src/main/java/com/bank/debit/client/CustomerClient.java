package com.bank.debit.client;

import com.bank.debit.exception.CustomerNotFoundException;
import com.bank.debit.exception.ServiceUnavailableException;
import com.bank.debit.model.dto.CustomerResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Client for communicating with Customer Service
 * Uses WebClient for reactive HTTP calls
 */
@Slf4j
@Component
public class CustomerClient {

    private final WebClient webClient;

    public CustomerClient(WebClient.Builder webClientBuilder,
                          @Value("${customer.service.url}") String customerServiceUrl) {
        this.webClient = webClientBuilder
                .baseUrl(customerServiceUrl)
                .build();
    }

    /**
     * Get customer by ID from Customer Service
     * @param customerId the customer id
     * @return Mono of CustomerResponse
     */
    @CircuitBreaker(name = "customerService", fallbackMethod = "getCustomerFallback")
    @Retry(name = "customerService")
    @TimeLimiter(name = "customerService")
    public Mono<CustomerResponse> getCustomerById(String customerId) {
        log.debug("Calling Customer Service to get customer with id: {}", customerId);

        return webClient.get()
                .uri("/api/customers/{id}", customerId)
                .retrieve()
                .onStatus(status -> status.value() == 404,
                        response -> Mono.error(new CustomerNotFoundException(customerId)))
                .bodyToMono(CustomerResponse.class)
                .timeout(Duration.ofSeconds(2))
                .doOnSuccess(customer -> log.debug("Customer found: {}", customer.getId()))
                .doOnError(ex -> {
                    log.error("Error calling Customer Service: {}", ex.getMessage());
                });
    }

    /**
     * Fallback method when circuit is open or service fails (for getCustomerById)
     */
    private Mono<CustomerResponse> getCustomerFallback(String customerId, Exception ex) {
        log.warn("Circuit breaker activated for getCustomerById. CustomerId: {}. Reason: {}",
                customerId, ex.getClass().getSimpleName());

        // Si es CustomerNotFoundException, propagarla (no es fallo del servicio)
        if (ex instanceof CustomerNotFoundException) {
            return Mono.error(ex);
        }

    // Para otros errores (timeout, 500, etc), retornar error de servicio no disponible
    return Mono.error(
        new ServiceUnavailableException(
            "Customer service is currently unavailable. Please try again later."));
    }

    /**
     * Check if customer exists
     * @param customerId the customer id
     * @return Mono of Boolean
     */
    @CircuitBreaker(name = "customerService", fallbackMethod = "customerExistsFallback")
    @Retry(name = "customerService")
    public Mono<Boolean> customerExists(String customerId) {
        return getCustomerById(customerId)
                .map(customer -> true)
                .defaultIfEmpty(false);
    }

    /**
     * Fallback for customerExists method
     */
    private Mono<Boolean> customerExistsFallback(String customerId, Exception ex) {
        log.warn("Circuit breaker activated for customerExists. CustomerId: {}. Reason: {}",
                customerId, ex.getClass().getSimpleName());

        // Si es CustomerNotFoundException, retornar false (es el comportamiento esperado)
        if (ex instanceof CustomerNotFoundException) {
            return Mono.just(false);
        }

        // Para errores reales del servicio, también retornar false pero loggear más
        log.error("Service unavailable when checking customer existence for id: {}", customerId);
        return Mono.just(false);
    }

    /**
     * Get delayed resulto to OPEN circuit breaker
     * @return Mono of String
     */
    @CircuitBreaker(name = "customerService", fallbackMethod = "getCustomerDelayFallback")
    @Retry(name = "customerService")
    @TimeLimiter(name = "customerService")
    public Mono<String> getDelayedResult() {
        return webClient.get()
                .uri("/api/customers/delay")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(2))
                .doOnSuccess(customer -> log.debug("Success delayed result"))
                .doOnError(ex -> log.error("Error delayed result: {}", ex.getMessage()));
    }

    // Fallback
    private Mono<String> getCustomerDelayFallback(Throwable ex) {
        return Mono.just("Fallback: customer service no disponible");
    }

}
