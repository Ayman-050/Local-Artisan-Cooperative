package com.example.demo.repository;

import com.example.demo.model.Order;
import com.example.demo.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByProductArtisanUserId(Long artisanId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product WHERE oi.order.orderId = :orderId")
    List<OrderItem> findByOrderIdWithProduct(@Param("orderId") Long orderId);

    Optional<OrderItem> findByOrderAndProduct_Id(Order order, Long productId);

    Optional<OrderItem> findByOrderItemId(Long orderItemId);

    @Query("SELECT oi FROM OrderItem oi LEFT JOIN FETCH oi.order LEFT JOIN FETCH oi.product")
    List<OrderItem> findAllWithOrderAndProduct();
}
