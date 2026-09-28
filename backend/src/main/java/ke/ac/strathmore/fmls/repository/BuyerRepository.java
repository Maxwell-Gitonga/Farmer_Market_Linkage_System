package ke.ac.strathmore.fmls.repository;

import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Buyer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuyerRepository extends JpaRepository<Buyer, Long> {
    Optional<Buyer> findByPhone(String phone);

    Optional<Buyer> findByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    boolean existsByEmailIgnoreCase(String email);

    List<Buyer> findByStatus(AccountStatus status);

    List<Buyer> findAllByOrderByCreatedAtDesc();

    long countByStatus(AccountStatus status);
}
