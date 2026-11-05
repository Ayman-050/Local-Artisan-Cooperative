package com.example.demo.controller;

import com.example.demo.repository.ProductRepository;
import com.example.demo.repository.AuctionRepository;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.model.Order;
import com.example.demo.service.AuctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.Optional;

@Controller
public class MainController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private AuctionRepository auctionRepository;

    @Autowired
    private AuctionService auctionService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private OrderRepository orderRepository;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("featuredProducts", productRepository.findAll().stream()
                .filter(p -> !"hidden".equalsIgnoreCase(p.getStatus()))
                .limit(6)
                .collect(java.util.stream.Collectors.toList()));
        return "index";
    }

    @GetMapping("/products")
    public String products(Model model) {
        model.addAttribute("products", productRepository.findByIsAuctionItem(false).stream()
                .filter(p -> !"hidden".equalsIgnoreCase(p.getStatus()))
                .collect(java.util.stream.Collectors.toList()));
        return "products";
    }

    @GetMapping("/auctions")
    public String auctions(Model model) {
        try {
            auctionService.syncStoredStatuses();
        } catch (Exception ex) {
            // Ignore
        }
        model.addAttribute("auctions", auctionRepository.findAll());
        model.addAttribute("categories", productRepository.findDistinctCategories());
        return "auctions";
    }

    @PostMapping("/auctions/bid")
    public String placeBid(@RequestParam Long auctionId, @RequestParam int amount,
            @RequestParam(required = false) String bidderName, Model model) {
        String name = (bidderName != null && !bidderName.isBlank()) ? bidderName : "Guest Bidder";
        var result = auctionService.placeBid(auctionId, amount, name, null);
        model.addAttribute("message", result.get("message"));
        return "redirect:/auctions";
    }

    @GetMapping("/cart")
    public String cart() {
        return "cart";
    }

    @GetMapping("/login")
    public String login() {
        return "Login";
    }

    @GetMapping("/profile")
    public String profile() {
        return "myprofile";
    }

    @GetMapping("/orders")
    public String orders() {
        return "myorders";
    }

    @GetMapping("/wishlist")
    public String wishlist() {
        return "mywishlist";
    }

    @GetMapping("/notifications")
    public String notifications() {
        return "notifications";
    }

    @GetMapping("/forgot-password")
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

    @GetMapping("/Login")
    public String loginAlt() {
        return "Login";
    }

    @GetMapping("/ForgetPassword")
    public String forgetPasswordAlt() {
        return "ForgetPassword";
    }

    @GetMapping("/myprofile")
    public String myProfile() {
        return "myprofile";
    }

    @GetMapping("/myorders")
    public String myOrders() {
        return "myorders";
    }

    @GetMapping("/mywishlist")
    public String myWishlist() {
        return "mywishlist";
    }

    @GetMapping("/OrderConformation")
    public String orderConformation(@RequestParam(required = false) Long orderId, Model model) {
        if (orderId != null) {
            Optional<Order> orderOpt = orderRepository.findById(orderId);
            orderOpt.ifPresent(order -> model.addAttribute("order", order));
        }
        return "OrderConformation";
    }

    @GetMapping("/ProductDetailsAuction")
    public String productDetailsAuction(@RequestParam(required = false) Long id, Model model) {
        try {
            auctionService.syncStoredStatuses();
        } catch (Exception ex) {
            // Ignore
        }
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
            productRepository.findById(id).ifPresent(product -> {
                model.addAttribute("product", product);
                model.addAttribute("reviews", reviewRepository.findByProductIdWithCustomerOrderByDateDesc(id));
                Double avg = reviewRepository.findAverageRatingByProductId(id);
                long count = reviewRepository.countByProductId(id);
                model.addAttribute("averageRating", avg != null ? Math.round(avg * 10.0) / 10.0 : 0.0);
                model.addAttribute("reviewCount", count);
            });
        }
        // Do not allow writing reviews directly from the product page; reviews can be
        // written from orders only.
        model.addAttribute("canWriteReview", false);
        return "ProductDetailsStandard";
    }
}
