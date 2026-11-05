package com.artsyvibe;

import com.artsyvibe.model.Admin;
import com.artsyvibe.model.Artisan;
import com.artsyvibe.model.Auction;
import com.artsyvibe.model.Customer;
import com.artsyvibe.model.OrderItem;
import com.artsyvibe.model.Product;
import com.artsyvibe.model.Review;
import com.artsyvibe.model.User;
import com.artsyvibe.repository.OrderItemRepository;
import com.artsyvibe.repository.ReviewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders every page as the role that uses it, with realistic data. Catches template errors
 * (e.g. Thymeleaf expressions that no longer work after a model change) without a browser.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestData.class)
class PageRenderingTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    ObjectMapper json;
    @Autowired
    ReviewRepository reviewRepository;
    @Autowired
    OrderItemRepository orderItemRepository;

    Admin admin;
    Artisan artisan;
    Customer customer;
    Product product;
    Auction liveAuction;
    Auction endedAuction;
    OrderItem deliveredItem;

    @BeforeEach
    void seed() {
        admin = data.admin();
        artisan = data.artisan();
        customer = data.customer();
        data.artisan("pending");
        product = data.product(artisan, "12.345", 4);
        liveAuction = data.liveAuction(artisan, "100", LocalDateTime.now().plusDays(1));
        endedAuction = data.endedAuctionWonBy(artisan, customer, "40", "75");
        deliveredItem = data.deliveredOrder(customer, product);
        deliveredItem.setRefundRequested(true);
        deliveredItem.setRefundStatus("ESCALATED");
        deliveredItem.setAdminRefundStatus("PENDING");
        deliveredItem.setRefundReason("<b>broken</b>");
        orderItemRepository.save(deliveredItem);
        data.placedOrder(customer, product, "pending");

        Review review = new Review();
        review.setProduct(product);
        review.setCustomer(customer);
        review.setRating(4);
        review.setComment("<script>alert(1)</script> lovely");
        review.setDate(LocalDate.now());
        review.setStatus("active");
        review.setAutoFlagged(true);
        reviewRepository.save(review);
        data.notification(customer);
        data.notification(artisan);
        data.notification(admin);
    }

    MockHttpSession login(User user) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", TestData.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    void assertRenders(MockHttpSession session, List<String> urls) throws Exception {
        for (String url : urls) {
            var request = get(url);
            if (session != null) {
                request.session(session);
            }
            try {
                mvc.perform(request).andExpect(status().isOk());
            } catch (AssertionError | Exception e) {
                throw new AssertionError("Page failed to render: " + url, e);
            }
        }
    }

    @Test
    void publicPagesRender() throws Exception {
        assertRenders(null, List.of("/", "/products", "/auctions", "/about", "/contact", "/login",
                "/forgot-password", "/ProductDetailsStandard?id=" + product.getId(),
                "/ProductDetailsAuction?id=" + liveAuction.getId(),
                "/ProductDetailsAuction?id=" + endedAuction.getId()));
    }

    @Test
    void customerPagesRender() throws Exception {
        assertRenders(login(customer), List.of("/cart", "/profile", "/orders", "/wishlist", "/notifications",
                "/orderConfirmation?orderId=" + deliveredItem.getOrder().getOrderId()));
    }

    @Test
    void artisanPagesRender() throws Exception {
        assertRenders(login(artisan), List.of("/artisanDashboard", "/artisanProducts", "/artisanAuction",
                "/artisanOrders", "/artisanAnalytics", "/artisanSettings", "/artisanNotification",
                "/artisanOrderDetail?orderId=" + deliveredItem.getOrder().getOrderId()));
    }

    @Test
    void adminPagesRender() throws Exception {
        assertRenders(login(admin), List.of("/adminDashboard", "/adminArtisanApproval", "/adminAuction",
                "/adminNotification", "/adminOrderDetail/" + deliveredItem.getOrder().getOrderId(),
                "/adminOrderManagement", "/adminOrderManagement?status=pending&dateFrom=2020-01-01",
                "/adminProducts", "/adminRefund", "/adminReports", "/adminReports?range=year",
                "/adminReview", "/adminUsersManagement"));
    }

    @Test
    void adminJsonEndpointsWork() throws Exception {
        MockHttpSession session = login(admin);
        mvc.perform(get("/api/admin/auctions/" + liveAuction.getId() + "/monitor").session(session))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/reports").session(session)).andExpect(status().isOk());
        mvc.perform(get("/api/notifications/" + admin.getUserId()).session(session)).andExpect(status().isOk());
    }
}
