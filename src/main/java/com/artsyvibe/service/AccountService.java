package com.artsyvibe.service;

import com.artsyvibe.model.Artisan;
import com.artsyvibe.model.Customer;
import com.artsyvibe.model.OrderStatus;
import com.artsyvibe.model.Product;
import com.artsyvibe.model.ProductStatus;
import com.artsyvibe.model.User;
import com.artsyvibe.model.UserStatus;
import com.artsyvibe.repository.AddressRepository;
import com.artsyvibe.repository.NotificationRepository;
import com.artsyvibe.repository.OrderItemRepository;
import com.artsyvibe.repository.OrderRepository;
import com.artsyvibe.repository.ProductRepository;
import com.artsyvibe.repository.UserRepository;
import com.artsyvibe.repository.WishlistRepository;
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
