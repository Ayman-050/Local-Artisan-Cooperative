package com.example.demo.service;

import com.example.demo.model.Artisan;
import com.example.demo.model.Customer;
import com.example.demo.model.OrderStatus;
import com.example.demo.model.Product;
import com.example.demo.model.ProductStatus;
import com.example.demo.model.User;
import com.example.demo.model.UserStatus;
import com.example.demo.repository.AddressRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.OrderItemRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes accounts. Users referenced by orders, reviews, bids and refunds cannot be hard-deleted without
 * breaking foreign keys (and the business needs that history), so accounts are anonymised instead:
 * personal data is wiped, the login is disabled, and the row stays to keep history consistent.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final WishlistRepository wishlistRepository;
    private final AddressRepository addressRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final NotificationRepository notificationRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void closeAccount(User user) {
        Long id = user.getUserId();

        if (user instanceof Customer customer) {
            wishlistRepository.findByCustomer(customer).ifPresent(wishlistRepository::delete);
            orderRepository.findByCustomerAndStatusIgnoreCase(customer, OrderStatus.CART).ifPresent(cart -> {
                orderItemRepository.deleteAll(orderItemRepository.findByOrderIdWithProduct(cart.getOrderId()));
                orderRepository.delete(cart);
            });
        }
        if (user instanceof Artisan) {
            // Their listings must not stay purchasable.
            for (Product product : productRepository.findByArtisanUserId(id)) {
                product.setStatus(ProductStatus.HIDDEN);
            }
        }
        if (user.getEmail() != null) {
            addressRepository.deleteAll(addressRepository.findByUserEmail(user.getEmail()));
        }
        notificationRepository.deleteAllForUser(id);

        user.setName("Deleted user #" + id);
        user.setEmail("deleted-user-" + id + "@deleted.invalid");
        user.setPhone(null);
        user.setPassword(null);
        user.setProfilePicture(User.DEFAULT_PROFILE_PICTURE);
        user.setStatus(UserStatus.DELETED);
        userRepository.save(user);
    }
}
