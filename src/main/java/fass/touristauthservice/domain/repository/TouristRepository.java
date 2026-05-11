package fass.touristauthservice.domain.repository;

import fass.touristauthservice.domain.model.Tourist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TouristRepository extends JpaRepository<Tourist, UUID> {
    Optional<Tourist> findByEmail(String email);
    boolean existsByEmail(String email);
}
