package ke.ac.strathmore.fmls.repository;

import ke.ac.strathmore.fmls.domain.PricePrediction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PricePredictionRepository extends JpaRepository<PricePrediction, Long> {
    Optional<PricePrediction> findTopByProductProductIdOrderByCreatedAtDesc(Long productId);

    Optional<PricePrediction> findTopByProductFarmerFarmerIdOrderByCreatedAtDesc(Long farmerId);

    long countByCreatedAtAfter(java.time.LocalDateTime from);
}
