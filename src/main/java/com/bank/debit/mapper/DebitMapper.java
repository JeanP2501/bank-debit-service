package com.bank.debit.mapper;

import com.bank.debit.model.CreateDebitCardRequest;
import com.bank.debit.model.DebitCardResponse;
import com.bank.debit.model.entity.Debit;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

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
        response.setCardNumber(entity.getCardNumber());
        response.setActive(entity.isActive());
        response.setCreatedAt(entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        response.setUpdatedAt(entity.getUpdatedAt()==null?
                null:entity.getUpdatedAt().atOffset(ZoneOffset.UTC));
        return response;
    }

}
