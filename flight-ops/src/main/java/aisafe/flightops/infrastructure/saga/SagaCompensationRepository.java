package aisafe.flightops.infrastructure.saga;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SagaCompensationRepository extends JpaRepository<SagaCompensationJpaEntity, Long> {
    List<SagaCompensationJpaEntity> findByFlightId(String flightId);
}
