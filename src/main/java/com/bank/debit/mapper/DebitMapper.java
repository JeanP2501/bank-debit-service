package com.bank.debit.mapper;

import com.bank.debit.model.CreateDebitCardRequest;
import com.bank.debit.model.DebitCardResponse;
import com.bank.debit.model.entity.Debit;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class DebitMapper {

    public Debit toEntity(CreateDebitCardRequest request) {
        return Debit.builder()
                .customerId(request.getCustomerId())
                .primaryAccountId(request.getPrimaryAccountId())
                .createdAt(LocalDateTime.now())
                .active(true)
                .build();
    }

    public DebitCardResponse toResponse(Debit entity) {
        DebitCardResponse response = new DebitCardResponse();
        response.setId(entity.getId());
        response.setCustomerId(entity.getCustomerId());
        response.setPrimaryAccountId(entity.getPrimaryAccountId());
        response.setAssociatedAccounts(entity.getAssociatedAccounts());
        response.setActive(entity.isActive());
        return response;
    }

}
