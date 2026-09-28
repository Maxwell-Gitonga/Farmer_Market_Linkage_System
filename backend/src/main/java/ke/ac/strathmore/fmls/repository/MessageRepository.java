package ke.ac.strathmore.fmls.repository;

import ke.ac.strathmore.fmls.domain.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("select m from Message m join fetch m.farmer join fetch m.buyer left join fetch m.product "
            + "where m.farmer.farmerId = :farmerId and m.buyer.buyerId = :buyerId order by m.createdAt asc")
    List<Message> conversation(@Param("farmerId") Long farmerId, @Param("buyerId") Long buyerId);

    @Query("select m from Message m join fetch m.farmer join fetch m.buyer "
            + "where m.farmer.farmerId = :farmerId order by m.createdAt desc")
    List<Message> forFarmer(@Param("farmerId") Long farmerId);

    @Query("select m from Message m join fetch m.farmer join fetch m.buyer "
            + "where m.buyer.buyerId = :buyerId order by m.createdAt desc")
    List<Message> forBuyer(@Param("buyerId") Long buyerId);
}
