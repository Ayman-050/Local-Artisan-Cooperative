package com.example.demo.repository;

import com.example.demo.model.Customer;
import com.example.demo.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByCustomerAndStatusIgnoreCase(Customer customer, String status);

    List<Order> findByCustomerAndStatusNotIgnoreCaseOrderByDateDesc(Customer customer, String status);

    /** Placed orders (not open carts) in a date range, inclusive. */
    @Query("SELECT o FROM Order o WHERE LOWER(o.status) <> 'cart' AND o.date BETWEEN :from AND :to")
    List<Order> findPlacedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(o) FROM Order o WHERE LOWER(o.status) <> 'cart' AND o.date BETWEEN :from AND :to")
    long countPlacedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    List<Order> findTop5ByStatusNotIgnoreCaseAndDateNotNullOrderByDateDescOrderIdDesc(String excludedStatus);

    /**
     * Admin "Orders" page: placed orders filtered and paged in SQL. {@code q} matches the order number or
     * customer name (LIKE pattern from PageView.likePattern); empty strings mean "no filter".
     */
    @EntityGraph(attributePaths = { "customer" })
    @Query("SELECT o FROM Order o LEFT JOIN o.customer c WHERE LOWER(o.status) <> 'cart' "
            + "AND o.date BETWEEN :from AND :to "
            + "AND (:q = '' OR LOWER(c.name) LIKE :q ESCAPE '\\' OR CAST(o.orderId AS string) LIKE :q ESCAPE '\\') "
            + "AND (:status = '' OR LOWER(o.status) = LOWER(:status))")
    Page<Order> searchForAdmin(@Param("q") String q, @Param("status") String status,
            @Param("from") LocalDate from, @Param("to") LocalDate to, Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE LOWER(o.status) <> 'cart' AND o.date BETWEEN :from AND :to")
    BigDecimal sumPlacedBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
