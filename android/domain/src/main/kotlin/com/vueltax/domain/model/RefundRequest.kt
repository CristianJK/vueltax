package com.vueltax.domain.model

enum class RefundStatus { DRAFT, READY, SUBMITTED, REFUNDED }

data class RefundRequest(
    val id: Long = 0,
    val tripId: Long,
    val status: RefundStatus,
    val estimatedRefundMinor: Long,
    val currency: String,
    val refundPointId: Long?,
)
