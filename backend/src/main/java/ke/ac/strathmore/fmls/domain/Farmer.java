package ke.ac.strathmore.fmls.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Farmer: lists produce, receives price recommendations and demand forecasts, views matched buyers. */
@Entity
@Table(name = "farmers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_farmers_phone", columnNames = "phone"),
        @UniqueConstraint(name = "uk_farmers_email", columnNames = "email")})
public class Farmer extends User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "farmer_id")
    private Long farmerId;

    @Override
    public Long getId() { return farmerId; }

    public void setFarmerId(Long farmerId) { this.farmerId = farmerId; }

    @Override
    public Role getRole() { return Role.FARMER; }
}
