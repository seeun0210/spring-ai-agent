package com.baedal.support.order;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class CancelHistoryService {

    private final List<CancelHistory> histories = new CopyOnWriteArrayList<>();

    public CancelHistory record(Order order, String reason, CancelOrderOutcome outcome, OffsetDateTime requestedAt) {
        CancelHistory history = new CancelHistory(
                UUID.randomUUID(),
                order.getOrderId(),
                order.getCustomerId(),
                reason == null || reason.isBlank() ? "고객 요청" : reason,
                outcome,
                requestedAt
        );
        histories.add(history);
        return history;
    }

    public Optional<CancelHistory> findLatestCanceled(String orderId, String customerId) {
        return histories.stream()
                .filter(history -> history.orderId().equals(orderId))
                .filter(history -> history.customerId().equals(customerId))
                .filter(history -> history.outcome() == CancelOrderOutcome.CANCELED)
                .max(Comparator.comparing(CancelHistory::requestedAt));
    }

    public List<CancelHistory> findByOrderId(String orderId) {
        return histories.stream()
                .filter(history -> history.orderId().equals(orderId))
                .toList();
    }
}
