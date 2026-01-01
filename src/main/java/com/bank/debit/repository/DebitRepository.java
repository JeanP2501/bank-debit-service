package com.bank.debit.repository;

import com.bank.debit.model.entity.Debit;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface DebitRepository extends ReactiveMongoRepository<Debit, String> {

    Mono<Debit> findByCustomerIdAndPrimaryAccountId(String customerId, String primaryAccountId);
    Mono<Debit> findByCustomerIdAndActiveTrue(String customerId);

}
