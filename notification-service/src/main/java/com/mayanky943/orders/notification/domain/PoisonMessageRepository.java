package com.mayanky943.orders.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PoisonMessageRepository extends JpaRepository<PoisonMessage, UUID> {
}
