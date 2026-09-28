package ke.ac.strathmore.fmls.repository;

import ke.ac.strathmore.fmls.domain.Match;
import ke.ac.strathmore.fmls.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchRepository extends JpaRepository<Match, Long> {

    @Query("select m from Match m join fetch m.buyer join fetch m.product where m.product.productId = :productId "
            + "order by m.matchScore desc")
    List<Match> forProduct(@Param("productId") Long productId);

    @Query("select m from Match m join fetch m.buyer join fetch m.product p where p.farmer.farmerId = :farmerId "
            + "and p.status = :status order by m.matchScore desc")
    List<Match> forFarmer(@Param("farmerId") Long farmerId, @Param("status") ProductStatus status);

    @Modifying
    @Query("delete from Match m where m.product.productId = :productId")
    void deleteForProduct(@Param("productId") Long productId);
}
