package fass.touristauthservice.domain.repository;

import fass.touristauthservice.domain.model.ActivationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, UUID> {
    Optional<ActivationToken> findByToken(String token);
    @Modifying
    @Query("UPDATE ActivationToken t SET t.used = true WHERE t.touristId = :touristId AND t.used = false")
    void markAllUnusedAsUsed(@Param("touristId") UUID touristId);
}
