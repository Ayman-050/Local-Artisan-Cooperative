package com.example.demo.repository;

import com.example.demo.model.Customer;
import com.example.demo.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByCustomerAndStatusIgnoreCase(Customer customer, String status);
    java.util.List<Order> findByCustomerAndStatusNotIgnoreCaseOrderByDateDesc(Customer customer, String status);
}
