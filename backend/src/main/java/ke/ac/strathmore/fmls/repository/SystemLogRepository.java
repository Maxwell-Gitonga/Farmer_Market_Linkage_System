package ke.ac.strathmore.fmls.repository;

import ke.ac.strathmore.fmls.domain.SystemLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SystemLogRepository extends JpaRepository<SystemLog, Long> {
    List<SystemLog> findTop300ByOrderByCreatedAtDesc();

    List<SystemLog> findTop300ByActionOrderByCreatedAtDesc(String action);

    long countByCreatedAtAfter(LocalDateTime from);
}
