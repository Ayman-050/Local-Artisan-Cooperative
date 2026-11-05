package com.example.demo.repository;

import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByProductArtisanUserId(Long artisanId);

    /** Lines of an order, ordered by product id (checkout locks products in this order). */
    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product WHERE oi.order.orderId = :orderId ORDER BY oi.product.id")
    List<OrderItem> findByOrderIdWithProduct(@Param("orderId") Long orderId);

    Optional<OrderItem> findByOrderAndProduct_Id(Order order, Long productId);

    Optional<OrderItem> findByOrderItemId(Long orderItemId);

    List<OrderItem> findByProduct_Id(Long productId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product p LEFT JOIN FETCH p.artisan WHERE oi.order.orderId = :orderId")
    List<OrderItem> findByOrderIdWithArtisan(@Param("orderId") Long orderId);

    @Query("SELECT oi FROM OrderItem oi LEFT JOIN FETCH oi.order LEFT JOIN FETCH oi.product")
    List<OrderItem> findAllWithOrderAndProduct();

    @Query("SELECT oi FROM OrderItem oi LEFT JOIN FETCH oi.order LEFT JOIN FETCH oi.product WHERE oi.refundRequested = true")
    List<OrderItem> findRefundRequests();

    /** Admin "Refunds" page: refund requests, undecided ones first, newest first. */
    @EntityGraph(attributePaths = { "order", "product" })
    @Query(value = "SELECT oi FROM OrderItem oi WHERE oi.refundRequested = true "
            + "ORDER BY CASE WHEN UPPER(oi.adminRefundStatus) = 'PENDING' THEN 0 ELSE 1 END, oi.orderItemId DESC",
            countQuery = "SELECT COUNT(oi) FROM OrderItem oi WHERE oi.refundRequested = true")
    Page<OrderItem> findRefundRequestsPage(Pageable pageable);

    @EntityGraph(attributePaths = { "order", "product" })
    List<OrderItem> findByRefundRequestedTrueAndAdminRefundStatusIgnoreCase(String adminRefundStatus);

    long countByRefundRequestedTrueAndAdminRefundStatusIgnoreCase(String adminRefundStatus);

    long countByRefundRequestedTrueAndRefundStatusIgnoreCaseAndAdminRefundStatusIgnoreCase(String refundStatus,
            String adminRefundStatus);

    /**
     * Refunds an admin decided (APPROVED / REJECTED) in a period. Rows decided before the decision time was
     * recorded fall back to the order date.
     */
    @Query("SELECT COUNT(oi) FROM OrderItem oi WHERE oi.refundRequested = true AND UPPER(oi.adminRefundStatus) = UPPER(:status) "
            + "AND ((oi.adminRefundProcessedAt BETWEEN :fromTime AND :toTime) "
            + "     OR (oi.adminRefundProcessedAt IS NULL AND oi.order.date BETWEEN :fromDate AND :toDate))")
    long countDecidedRefunds(@Param("status") String status, @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /** Lines of placed (non-cart) orders dated in a range, with order and product loaded. */
    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o LEFT JOIN FETCH oi.product "
            + "WHERE LOWER(o.status) <> 'cart' AND o.date BETWEEN :from AND :to")
    List<OrderItem> findPlacedItemsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("SELECT COUNT(oi) FROM OrderItem oi WHERE oi.refundRequested = true AND oi.order.date BETWEEN :from AND :to")
    long countRefundRequestsByOrderDate(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @EntityGraph(attributePaths = { "order" })
    List<OrderItem> findTop5ByRefundRequestedTrueOrderByOrderItemIdDesc();

    /** True when the order contains at least one product sold by the artisan. */
    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.order.orderId = :orderId AND oi.product.artisan.userId = :artisanId")
    boolean existsByOrderAndArtisan(@Param("orderId") Long orderId, @Param("artisanId") Long artisanId);

    /** True when the customer has a delivered order containing the product. */
    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.product.id = :productId "
            + "AND oi.order.customer.userId = :customerId "
            + "AND (LOWER(oi.fulfilmentStatus) = 'delivered' OR LOWER(oi.order.status) = 'delivered')")
    boolean hasDeliveredPurchase(@Param("customerId") Long customerId, @Param("productId") Long productId);
}
