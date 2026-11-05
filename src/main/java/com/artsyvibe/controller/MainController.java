package com.artsyvibe.controller;

import com.artsyvibe.model.ProductStatus;
import com.artsyvibe.model.Roles;
import com.artsyvibe.repository.AuctionRepository;
import com.artsyvibe.repository.OrderRepository;
import com.artsyvibe.repository.ProductRepository;
import com.artsyvibe.repository.ReviewRepository;
import com.artsyvibe.security.CurrentUser;
import com.artsyvibe.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Public storefront pages and the customer page shells (their data is loaded by JS from /api). */
@Controller
@RequiredArgsConstructor
public class MainController {

    private static final int FEATURED_PRODUCTS = 6;

    private final ProductRepository productRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionService auctionService;
    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final CurrentUser currentUser;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("featuredProducts", productRepository.findVisible(PageRequest.of(0, FEATURED_PRODUCTS)));
        return "index";
    }

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("products", productRepository.findVisibleStandardProducts());
        return "products";
    }

    @GetMapping("/auctions")
    public String auctions(Model model) {
        model.addAttribute("auctions", auctionRepository.findAllWithProduct());
        model.addAttribute("categories", productRepository.findDistinctCategories());
        return "auctions";
    }

    @GetMapping("/cart")
    public String cart() {
        return "cart";
    }

    @GetMapping({ "/login", "/Login" })
    public String login() {
        return "Login";
    }

    @GetMapping({ "/profile", "/myprofile" })
    public String profile() {
        return "myprofile";
    }

    @GetMapping({ "/orders", "/myorders" })
    public String orders() {
        return "myorders";
    }

    @GetMapping({ "/wishlist", "/mywishlist" })
    public String wishlist() {
        return "mywishlist";
    }

    @GetMapping("/notifications")
    public String notifications() {
        return "notifications";
    }

    @GetMapping({ "/forgot-password", "/ForgetPassword" })
    public String forgotPassword() {
        return "ForgetPassword";
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/contact")
    public String contact() {
        return "contact";
    }

    @GetMapping("/index")
    public String indexAlt() {
        return "redirect:/";
    }

    /** Old, misspelled address of the confirmation page; kept so existing links still work. */
    @GetMapping("/OrderConformation")
    public String legacyOrderConfirmation(@RequestParam(required = false) Long orderId) {
        return "redirect:/orderConfirmation" + (orderId != null ? "?orderId=" + orderId : "");
    }

    /** Order confirmation; only the customer who placed the order (or an admin) can see it. */
    @GetMapping("/orderConfirmation")
    public String orderConfirmation(@RequestParam(required = false) Long orderId, Model model) {
        if (orderId != null) {
            boolean isAdmin = currentUser.hasRole(Roles.ADMIN);
            currentUser.id().flatMap(me -> orderRepository.findById(orderId)
                    .filter(order -> isAdmin || (order.getCustomer() != null
                            && me.equals(order.getCustomer().getUserId()))))
                    .ifPresent(order -> model.addAttribute("order", order));
        }
        return "orderConfirmation";
    }

    @GetMapping("/ProductDetailsAuction")
    public String productDetailsAuction(@RequestParam(required = false) Long id, Model model) {
        if (id != null) {
            auctionRepository.findById(id).ifPresent(auction -> {
                model.addAttribute("auction", auction);
                model.addAttribute("displayStatus", auctionService.resolveDisplayStatus(auction));
            });
        }
        return "ProductDetailsAuction";
    }

    @GetMapping("/ProductDetailsStandard")
    public String productDetailsStandard(@RequestParam(required = false) Long id, Model model) {
        if (id != null) {
            productRepository.findById(id)
                    .filter(p -> !ProductStatus.HIDDEN.equalsIgnoreCase(p.getStatus()) || currentUser.hasRole(Roles.ADMIN)
                            || currentUser.id().map(me -> p.getArtisan() != null
                                    && me.equals(p.getArtisan().getUserId())).orElse(false))
                    .ifPresent(product -> {
                        model.addAttribute("product", product);
                        model.addAttribute("reviews", reviewRepository.findByProductIdWithCustomerOrderByDateDesc(id));
                        Double avg = reviewRepository.findAverageRatingByProductId(id);
                        model.addAttribute("averageRating", avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
                        model.addAttribute("reviewCount", reviewRepository.countByProductId(id));
                    });
        }
        // Reviews can only be written from the customer's orders page.
        model.addAttribute("canWriteReview", false);
        return "ProductDetailsStandard";
    }
}
