package ke.ac.strathmore.fmls.repository;

import ke.ac.strathmore.fmls.domain.AccountStatus;
import ke.ac.strathmore.fmls.domain.Product;
import ke.ac.strathmore.fmls.domain.ProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** getAvailableListings(cropType) – buyer search (sequence diagram 2). */
    @Query("select p from Product p join fetch p.farmer f "
            + "where p.status = :status and f.status = :farmerStatus "
            + "and (:crop is null or lower(p.cropType) like lower(concat('%', :crop, '%'))) "
            + "and (:county is null or lower(p.location) = lower(:county)) "
            + "and (:maxPrice is null or p.pricePerUnit <= :maxPrice) "
            + "order by p.createdAt desc")
    List<Product> search(@Param("status") ProductStatus status,
                         @Param("farmerStatus") AccountStatus farmerStatus,
                         @Param("crop") String crop,
                         @Param("county") String county,
                         @Param("maxPrice") Double maxPrice);

    /** Row lock so two buyers cannot buy the same quantity at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.productId = :id")
    java.util.Optional<Product> findByIdForUpdate(@Param("id") Long id);

    List<Product> findByFarmerFarmerIdAndStatusInOrderByUpdatedAtDesc(Long farmerId, Collection<ProductStatus> statuses);

    List<Product> findTop20ByFarmerFarmerIdAndStatusOrderByUpdatedAtDesc(Long farmerId, ProductStatus status);

    long countByFarmerFarmerIdAndStatus(Long farmerId, ProductStatus status);

    long countByStatus(ProductStatus status);

    @Query("select p from Product p join fetch p.farmer where p.status = :status order by p.createdAt desc")
    List<Product> findByStatusWithFarmer(@Param("status") ProductStatus status);

    @Query("select p from Product p join fetch p.farmer where p.status in :statuses order by p.createdAt desc")
    List<Product> findByStatusesWithFarmer(@Param("statuses") Collection<ProductStatus> statuses);
}
