package ke.ac.strathmore.fmls.repository;

import ke.ac.strathmore.fmls.domain.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByEmailIgnoreCase(String email);

    Optional<Admin> findByPhone(String phone);
}
