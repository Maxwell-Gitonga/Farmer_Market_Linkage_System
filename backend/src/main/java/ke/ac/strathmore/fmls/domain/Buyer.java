package ke.ac.strathmore.fmls.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Buyer: searches listings, views recommended products and pricing insights, purchases produce. */
@Entity
@Table(name = "buyers", uniqueConstraints = {
        @UniqueConstraint(name = "uk_buyers_phone", columnNames = "phone"),
        @UniqueConstraint(name = "uk_buyers_email", columnNames = "email")})
public class Buyer extends User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "buyer_id")
    private Long buyerId;

    /** Comma separated crop types the buyer is interested in (used by the recommendation engine). */
    @Column(length = 255)
    private String preferences;

    @Override
    public Long getId() { return buyerId; }

    public void setBuyerId(Long buyerId) { this.buyerId = buyerId; }

    @Override
    public Role getRole() { return Role.BUYER; }

    public String getPreferences() { return preferences; }
    public void setPreferences(String preferences) { this.preferences = preferences; }
}
