package ke.ac.strathmore.fmls.repository;

import ke.ac.strathmore.fmls.domain.MarketPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MarketPriceRepository extends JpaRepository<MarketPrice, Long> {

    List<MarketPrice> findByCropTypeIgnoreCaseAndRecordedDateGreaterThanEqualOrderByRecordedDateAsc(String cropType,
                                                                                                    LocalDate from);

    List<MarketPrice> findByRecordedDateGreaterThanEqual(LocalDate from);

    @Query("select max(m.recordedDate) from MarketPrice m")
    LocalDate latestDate();

    @Query("select max(m.recordedDate) from MarketPrice m where m.source = :source")
    LocalDate latestDateForSource(@Param("source") String source);

    /** Compact projection for model training: crop, market, price, date. */
    @Query("select m.cropType, m.marketLocation, m.price, m.recordedDate from MarketPrice m")
    List<Object[]> trainingRows();

    @Query("select distinct m.cropType from MarketPrice m order by m.cropType")
    List<String> distinctCrops();

    List<MarketPrice> findTop100ByOrderByRecordedDateDescPriceIdDesc();
}
