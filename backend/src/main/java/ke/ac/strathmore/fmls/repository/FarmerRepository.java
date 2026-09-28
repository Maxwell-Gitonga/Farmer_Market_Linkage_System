package ke.ac.strathmore.fmls.repository;

import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Farmer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FarmerRepository extends JpaRepository<Farmer, Long> {
    Optional<Farmer> findByPhone(String phone);

    Optional<Farmer> findByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    List<Farmer> findByStatusOrderByCreatedAtDesc(AccountStatus status);

    List<Farmer> findAllByOrderByCreatedAtDesc();

    long countByStatus(AccountStatus status);
}
