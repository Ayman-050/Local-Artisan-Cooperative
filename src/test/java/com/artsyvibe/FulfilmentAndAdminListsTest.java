package com.artsyvibe;

import com.artsyvibe.model.Artisan;
import com.artsyvibe.model.Customer;
import com.artsyvibe.model.Order;
import com.artsyvibe.model.OrderItem;
import com.artsyvibe.model.OrderStatus;
import com.artsyvibe.model.Product;
import com.artsyvibe.model.User;
import com.artsyvibe.repository.OrderItemRepository;
import com.artsyvibe.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestData.class)
class FulfilmentAndAdminListsTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    ObjectMapper json;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    OrderItemRepository orderItemRepository;

    MockHttpSession login(User user) throws Exception {
        return (MockHttpSession) mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", user.getEmail(), "password", TestData.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);
    }

    void setStatus(MockHttpSession session, Long orderId, String status) throws Exception {
        mvc.perform(put("/api/orders/" + orderId + "/status").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    String lineStatus(Long orderId, Artisan artisan) {
        return orderItemRepository.findByOrderIdWithArtisan(orderId).stream()
                .filter(i -> i.getProduct().getArtisan().getUserId().equals(artisan.getUserId()))
                .map(OrderItem::getFulfilmentStatus).findFirst().orElseThrow();
    }

    @Test
    void eachArtisanOnlyFulfilsTheirOwnItems() throws Exception {
        Artisan a = data.artisan();
        Artisan b = data.artisan();
        Customer customer = data.customer();
        Order order = data.multiArtisanOrder(customer, data.product(a, "10", 5), data.product(b, "20", 5));
        MockHttpSession sessionA = login(a);
        MockHttpSession sessionB = login(b);

        setStatus(sessionA, order.getOrderId(), "shipped");
        assertThat(lineStatus(order.getOrderId(), a)).isEqualTo("shipped");
        assertThat(lineStatus(order.getOrderId(), b)).isEqualTo("pending");
        // One artisan shipping does not make the whole order "shipped".
        assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.PROCESSING);

        setStatus(sessionB, order.getOrderId(), "shipped");
        assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);

        setStatus(sessionA, order.getOrderId(), "delivered");
        assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.SHIPPED);
        setStatus(sessionB, order.getOrderId(), "delivered");
        assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void artisanPagesShowTheirOwnLineStatus() throws Exception {
        Artisan a = data.artisan();
        Artisan b = data.artisan();
        Order order = data.multiArtisanOrder(data.customer(), data.product(a, "10", 5), data.product(b, "20", 5));
        MockHttpSession sessionA = login(a);
        setStatus(sessionA, order.getOrderId(), "shipped");

        mvc.perform(get("/artisanOrderDetail").param("orderId", String.valueOf(order.getOrderId())).session(sessionA))
                .andExpect(status().isOk())
                .andExpect(model().attribute("artisanStatus", "shipped"));
        mvc.perform(get("/artisanOrderDetail").param("orderId", String.valueOf(order.getOrderId())).session(login(b)))
                .andExpect(model().attribute("artisanStatus", "pending"));
    }

    @Test
    void adminStatusChangeAppliesToEveryLine() throws Exception {
        Artisan a = data.artisan();
        Artisan b = data.artisan();
        Order order = data.multiArtisanOrder(data.customer(), data.product(a, "10", 5), data.product(b, "20", 5));
        setStatus(login(data.admin()), order.getOrderId(), "delivered");
        assertThat(lineStatus(order.getOrderId(), a)).isEqualTo("delivered");
        assertThat(lineStatus(order.getOrderId(), b)).isEqualTo("delivered");
        assertThat(orderRepository.findById(order.getOrderId()).orElseThrow().getStatus()).isEqualTo("delivered");
    }

    @Test
    void customerCanRefundAnItemDeliveredByItsArtisan() throws Exception {
        Artisan a = data.artisan();
        Customer customer = data.customer();
        Order order = data.multiArtisanOrder(customer, data.product(a, "10", 5), data.product(data.artisan(), "20", 5));
        setStatus(login(a), order.getOrderId(), "delivered");
        Long itemA = orderItemRepository.findByOrderIdWithArtisan(order.getOrderId()).stream()
                .filter(i -> i.getProduct().getArtisan().getUserId().equals(a.getUserId()))
                .findFirst().orElseThrow().getOrderItemId();

        mvc.perform(post("/api/orders/items/" + itemA + "/refund").with(csrf()).session(login(customer))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Broken\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void aggregateRules() {
        assertThat(OrderStatus.aggregate(List.of("pending", "pending"))).isEqualTo("pending");
        assertThat(OrderStatus.aggregate(List.of("pending", "shipped"))).isEqualTo("processing");
        assertThat(OrderStatus.aggregate(List.of("shipped", "delivered"))).isEqualTo("shipped");
        assertThat(OrderStatus.aggregate(List.of("delivered", "delivered"))).isEqualTo("delivered");
    }

    @Test
    void adminUserSearchFiltersAndPagesOnTheServer() throws Exception {
        MockHttpSession admin = login(data.admin());
        Customer target = data.customer();
        Customer other = data.customer();

        mvc.perform(get("/adminUsersManagement").param("q", target.getEmail()).session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(target.getEmail())))
                .andExpect(content().string(not(containsString(other.getEmail()))));
        mvc.perform(get("/adminUsersManagement").param("role", "artisan").param("q", target.getEmail()).session(admin))
                .andExpect(content().string(not(containsString(target.getEmail()))));

        // 25 extra users guarantees at least two pages of 20.
        for (int i = 0; i < 25; i++) {
            data.customer();
        }
        var page2 = mvc.perform(get("/adminUsersManagement").param("page", "2").session(admin))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel();
        com.artsyvibe.web.PageView pager = (com.artsyvibe.web.PageView) page2.get("pager");
        assertThat(pager.number()).isEqualTo(2);
        assertThat(pager.from()).isEqualTo(21);
        assertThat((List<?>) page2.get("usersList")).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void oldMisspelledAddressesRedirectToTheNewOnes() throws Exception {
        MockHttpSession admin = login(data.admin());
        mvc.perform(get("/adminOrderManagment?status=pending").session(admin))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/adminOrderManagement?status=pending"));
        mvc.perform(get("/OrderConformation").param("orderId", "5").session(admin))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/orderConfirmation?orderId=5"));
    }

    @Test
    void adminProductAndOrderSearch() throws Exception {
        MockHttpSession admin = login(data.admin());
        Artisan artisan = data.artisan();
        Product wanted = data.product(artisan, "10", 5);
        Product unwanted = data.product(data.artisan(), "10", 5);
        OrderItem item = data.deliveredOrder(data.customer(), wanted);

        mvc.perform(get("/adminProducts").param("q", wanted.getTitle()).session(admin))
                .andExpect(content().string(containsString(wanted.getTitle())))
                .andExpect(content().string(not(containsString(unwanted.getTitle()))));
        mvc.perform(get("/adminProducts").param("status", "hidden").param("q", wanted.getTitle()).session(admin))
                .andExpect(content().string(not(containsString(wanted.getTitle()))));
        mvc.perform(get("/adminOrderManagement").param("search", String.valueOf(item.getOrder().getOrderId()))
                        .param("status", "delivered").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ORD-" + item.getOrder().getOrderId())));
        mvc.perform(get("/adminReview").param("status", "rejected").param("flagged", "true").param("rating", "5")
                        .param("q", "nothing-matches-this").session(admin))
                .andExpect(status().isOk());
        mvc.perform(get("/adminRefund").param("page", "1").session(admin)).andExpect(status().isOk());
        mvc.perform(get("/adminReports").param("range", "custom").param("start", "2020-01-01").session(admin))
                .andExpect(status().isOk());
    }
}
