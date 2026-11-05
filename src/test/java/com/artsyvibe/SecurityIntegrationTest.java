package com.artsyvibe;

import com.artsyvibe.config.LegacyPasswordUpgrader;
import com.artsyvibe.model.Artisan;
import com.artsyvibe.model.Customer;
import com.artsyvibe.model.Notification;
import com.artsyvibe.model.OrderItem;
import com.artsyvibe.model.Product;
import com.artsyvibe.model.UserStatus;
import com.artsyvibe.repository.ProductRepository;
import com.artsyvibe.repository.UserRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestData.class)
class SecurityIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    TestData data;
    @Autowired
    ObjectMapper json;
    @Autowired
    UserRepository userRepository;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    LegacyPasswordUpgrader legacyPasswordUpgrader;

    MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    MockHttpSession login(com.artsyvibe.model.User user) throws Exception {
        return login(user.getEmail(), TestData.PASSWORD);
    }

    @Test
    void loginResponseNeverContainsThePasswordHash() throws Exception {
        Customer c = data.customer();
        String body = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", c.getEmail(), "password", TestData.PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains(c.getEmail()).doesNotContain("password").doesNotContain("$2a$");
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        Customer c = data.customer();
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", c.getEmail(), "password", "nope"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void writesWithoutCsrfTokenAreRejected() throws Exception {
        Customer c = data.customer();
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", c.getEmail(), "password", TestData.PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiRequiresASessionAndIgnoresTheXUserEmailHeader() throws Exception {
        Customer victim = data.customer();
        mvc.perform(get("/api/cart").header("X-User-Email", victim.getEmail()))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/orders").header("X-User-Email", victim.getEmail()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionIdentityWinsOverSpoofedEmail() throws Exception {
        Customer me = data.customer();
        Customer other = data.customer();
        mvc.perform(get("/api/user/me").session(login(me)).param("email", other.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(me.getEmail()));
    }

    @Test
    void adminAreaIsClosedToEveryoneElse() throws Exception {
        mvc.perform(get("/adminDashboard")).andExpect(status().is3xxRedirection());
        MockHttpSession customer = login(data.customer());
        mvc.perform(get("/adminDashboard").session(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/users/1/delete").with(csrf()).session(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/approve-artisan/1").with(csrf()).session(customer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/orders/items/1/admin-refund/decision").with(csrf()).session(customer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"approve\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void artisanPagesShowOnlyTheSignedInArtisan() throws Exception {
        Artisan me = data.artisan();
        Artisan other = data.artisan();
        mvc.perform(get("/artisanDashboard").param("id", String.valueOf(other.getUserId())).session(login(me)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("artisan", org.hamcrest.Matchers.hasProperty("userId",
                        org.hamcrest.Matchers.is(me.getUserId()))));
        mvc.perform(get("/artisanDashboard").session(login(data.customer()))).andExpect(status().isForbidden());
    }

    @Test
    void artisansCannotEditOrDeleteOtherArtisansProducts() throws Exception {
        Artisan owner = data.artisan();
        Product product = data.product(owner, "10", 5);
        MockHttpSession intruder = login(data.artisan());

        mvc.perform(put("/api/products/" + product.getId()).with(csrf()).session(intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"hacked\",\"price\":1,\"stockQuantity\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/products/" + product.getId()).with(csrf()).session(intruder))
                .andExpect(status().isNotFound());
        assertThat(productRepository.findById(product.getId()).orElseThrow().getTitle()).isEqualTo(product.getTitle());
    }

    @Test
    void createdProductBelongsToTheSessionArtisanNotTheEmailInTheBody() throws Exception {
        Artisan me = data.artisan();
        Artisan other = data.artisan();
        mvc.perform(post("/api/products").with(csrf()).session(login(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("artisanEmail", other.getEmail(), "title", "Bowl",
                                "price", "12.500", "stockQuantity", 3, "isAuctionItem", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.artisan.userId").value(me.getUserId()))
                .andExpect(jsonPath("$.product.artisan.email").doesNotExist())
                .andExpect(jsonPath("$.product.price").value(12.5));
    }

    @Test
    void notificationsOfOtherUsersAreOffLimits() throws Exception {
        Customer me = data.customer();
        Customer other = data.customer();
        Notification theirs = data.notification(other);
        MockHttpSession session = login(me);
        mvc.perform(get("/api/notifications/" + other.getUserId()).session(session)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/notifications/" + theirs.getId()).with(csrf()).session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void customersCanOnlyRequestRefundsOnTheirOwnDeliveredOrders() throws Exception {
        Artisan artisan = data.artisan();
        Customer owner = data.customer();
        OrderItem delivered = data.deliveredOrder(owner, data.product(artisan, "20", 5));
        OrderItem pending = data.placedOrder(owner, data.product(artisan, "20", 5), "pending");
        String body = "{\"reason\":\"Broken\"}";

        mvc.perform(post("/api/orders/items/" + delivered.getOrderItemId() + "/refund").with(csrf())
                        .session(login(data.customer())).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        MockHttpSession ownerSession = login(owner);
        mvc.perform(post("/api/orders/items/" + pending.getOrderItemId() + "/refund").with(csrf())
                        .session(ownerSession).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/orders/items/" + delivered.getOrderItemId() + "/refund").with(csrf())
                        .session(ownerSession).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/orders/items/" + delivered.getOrderItemId() + "/refund").with(csrf())
                        .session(ownerSession).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingAnAccountRequiresThePasswordAndAnonymisesIt() throws Exception {
        Customer c = data.customer();
        MockHttpSession session = login(c);
        mvc.perform(delete("/api/auth/delete-account").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"wrong\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/auth/delete-account").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + TestData.PASSWORD + "\"}"))
                .andExpect(status().isOk());

        var closed = userRepository.findById(c.getUserId()).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(closed.getEmail()).doesNotContain(c.getEmail());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", c.getEmail(), "password", TestData.PASSWORD))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void suspendingAUserEndsTheirExistingSession() throws Exception {
        Customer c = data.customer();
        MockHttpSession session = login(c);
        mvc.perform(get("/api/cart").session(session)).andExpect(status().isOk());

        c.setStatus(UserStatus.SUSPENDED);
        userRepository.save(c);
        mvc.perform(get("/api/cart").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyPlainTextPasswordsAreHashedAtStartupAndStillWork() throws Exception {
        Customer c = data.customer();
        c.setPassword("plainSecret9");
        userRepository.save(c);

        legacyPasswordUpgrader.run(null);

        assertThat(userRepository.findById(c.getUserId()).orElseThrow().getPassword()).startsWith("$2");
        login(c.getEmail(), "plainSecret9");
    }

    @Test
    void registrationIgnoresPrivilegedFieldsInTheBody() throws Exception {
        String email = "new-" + TestData.uid() + "@test.local";
        mvc.perform(post("/api/auth/register/customer").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "New " + TestData.uid(), "email", email,
                                "phone", "+97333334444", "password", "Password1",
                                "role", "ADMIN", "userId", 1, "totalSpent", 999))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.user.totalSpent").value(0));
        assertThat(userRepository.findByEmailIgnoreCase(email).orElseThrow().getRole()).isEqualTo("CUSTOMER");
    }

    @Test
    void weakPasswordsAreRejectedAtRegistration() throws Exception {
        mvc.perform(post("/api/auth/register/customer").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Weak " + TestData.uid(),
                                "email", "weak-" + TestData.uid() + "@test.local",
                                "phone", "+97333334444", "password", "abc"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hiddenProductsAreNotPublic() throws Exception {
        Product p = data.product(data.artisan(), "5", 1);
        p.setStatus("hidden");
        productRepository.save(p);
        mvc.perform(get("/api/products/" + p.getId())).andExpect(status().isNotFound());
        assertThat(p.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }
}
