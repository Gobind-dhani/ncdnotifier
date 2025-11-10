package com.ibsec.ncdnotifier.repository;


import com.ibsec.ncdnotifier.entity.BondMaturityNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BondMaturityNotificationRepository extends JpaRepository<BondMaturityNotificationLog, Long> {
}
