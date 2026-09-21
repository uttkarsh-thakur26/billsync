package com.uttkarsh.billsync.repository;

import com.uttkarsh.billsync.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByGroupId(Long groupId);
}
