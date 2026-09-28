package ke.ac.strathmore.fmls.repository;

import ke.ac.strathmore.fmls.domain.DemandForecast;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DemandForecastRepository extends JpaRepository<DemandForecast, Long> {
    Optional<DemandForecast> findTopByCropTypeIgnoreCaseOrderByGeneratedAtDesc(String cropType);
}
