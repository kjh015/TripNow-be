package com.traveler.useractivity.domain.rule.process.repository;

import com.traveler.useractivity.domain.rule.process.entity.LogProcess;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogProcessRepository extends JpaRepository<LogProcess, Long> {
    Optional<LogProcess> findByName(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
