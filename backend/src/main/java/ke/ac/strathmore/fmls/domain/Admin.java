package ke.ac.strathmore.fmls.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Administrator: manages users, approves farmers, removes listings, monitors activity, views reports. */
@Entity
@Table(name = "admins", uniqueConstraints = @UniqueConstraint(name = "uk_admins_email", columnNames = "email"))
public class Admin extends User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long adminId;

    @Override
    public Long getId() { return adminId; }

    public void setAdminId(Long adminId) { this.adminId = adminId; }

    @Override
    public Role getRole() { return Role.ADMIN; }
}
