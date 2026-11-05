package com.example.demo;

import com.example.demo.model.Artisan;
import com.example.demo.model.Auction;
import com.example.demo.model.Customer;
import com.example.demo.model.Product;
import com.example.demo.repository.AuctionRepository;
import com.example.demo.repository.CustomerRepository;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import com.example.demo.service.AuctionService;
import com.example.demo.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestData.class)
class CommerceRulesTest {

    @Autowired
    TestData data;
    @Autowired
    CartService cartService;
    @Autowired
    AuctionService auctionService;
    @Autowired
    ProductRepository productRepository;
    @Autowired
    AuctionRepository auctionRepository;
    @Autowired
    OrderRepository orderRepository;
    @Autowired
    CustomerRepository customerRepository;

    /** Runs the tasks at the same moment and returns how many completed without throwing. */
    private static int runConcurrently(List<Callable<Boolean>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        for (Callable<Boolean> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    return task.call();
                } catch (Exception e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int ok = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) {
                ok++;
            }
        }
        pool.shutdown();
        return ok;
    }

    @Test
    void twoBuyersCannotBothBuyTheLastUnit() throws Exception {
        Artisan artisan = data.artisan();
        Product lastOne = data.product(artisan, "15.250", 1);
        List<Customer> buyers = List.of(data.customer(), data.customer(), data.customer());
        for (Customer buyer : buyers) {
            cartService.addItem(buyer, lastOne.getId(), 1);
        }

        List<Callable<Boolean>> checkouts = new ArrayList<>();
        for (Customer buyer : buyers) {
            checkouts.add(() -> {
                cartService.checkout(buyer, "card", "pickup", null);
                return true;
            });
        }
        int succeeded = runConcurrently(checkouts);

        assertThat(succeeded).isEqualTo(1);
        Product after = productRepository.findById(lastOne.getId()).orElseThrow();
        assertThat(after.getStockQuantity()).isZero();
        assertThat(after.getStatus()).isEqualTo("out of stock");
    }

    @Test
    void checkoutChargesExactDecimalTotals() {
        Artisan artisan = data.artisan();
        Customer buyer = data.customer();
        Product p = data.product(artisan, "0.100", 10);
        cartService.addItem(buyer, p.getId(), 3);

        Map<String, Object> result = cartService.checkout(buyer, "card", "ship", data.address(buyer).getId());

        // 3 x 0.100 + 2.000 shipping = 2.300 exactly (doubles would give 2.3000000000000003).
        assertThat((BigDecimal) result.get("totalAmount")).isEqualByComparingTo("2.300");
        assertThat(customerRepository.findById(buyer.getUserId()).orElseThrow().getTotalSpent())
                .isEqualByComparingTo("2.300");
    }

    @Test
    void checkoutRejectsAnotherCustomersAddress() {
        Customer buyer = data.customer();
        Product p = data.product(data.artisan(), "5", 10);
        cartService.addItem(buyer, p.getId(), 1);
        Long someoneElsesAddress = data.address(data.customer()).getId();

        assertThatThrownBy(() -> cartService.checkout(buyer, "card", "ship", someoneElsesAddress))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void auctionWinnerPaysTheWinningBidNotTheStartingPrice() {
        Artisan artisan = data.artisan();
        Customer winner = data.customer();
        Auction auction = data.endedAuctionWonBy(artisan, winner, "50", "120");

        Map<String, Object> cart = cartService.addItem(winner, auction.getProduct().getId(), 1);

        assertThat((BigDecimal) cart.get("subtotal")).isEqualByComparingTo("120");
        assertThatThrownBy(() -> cartService.addItem(data.customer(), auction.getProduct().getId(), 1))
                .hasMessageContaining("winning bidder");
    }

    @Test
    void simultaneousEqualBidsDoNotBothWin() throws Exception {
        Auction auction = data.liveAuction(data.artisan(), "100", LocalDateTime.now().plusHours(2));
        List<Customer> bidders = List.of(data.customer(), data.customer(), data.customer(), data.customer());

        List<Callable<Boolean>> bids = new ArrayList<>();
        for (Customer bidder : bidders) {
            bids.add(() -> Boolean.TRUE.equals(auctionService.placeBid(auction.getId(), 150, bidder).get("success")));
        }
        int accepted = runConcurrently(bids);

        assertThat(accepted).isEqualTo(1);
        assertThat(auctionRepository.findById(auction.getId()).orElseThrow().getCurrentHighestBid())
                .isEqualByComparingTo("150");
    }

    @Test
    void bidsMustBeatTheCurrentBidAndTheAuctionMustBeLive() {
        Auction auction = data.liveAuction(data.artisan(), "100", LocalDateTime.now().plusHours(2));
        Customer a = data.customer();
        Customer b = data.customer();

        assertThat(auctionService.placeBid(auction.getId(), 100, a).get("success")).isEqualTo(true);
        assertThat(auctionService.placeBid(auction.getId(), 100, b).get("success")).isEqualTo(false);
        assertThat(auctionService.placeBid(auction.getId(), 105, a).get("message"))
                .isEqualTo("You are already the highest bidder.");

        Auction ended = data.endedAuctionWonBy(data.artisan(), a, "10", "20");
        assertThat(auctionService.placeBid(ended.getId(), 500, b).get("success")).isEqualTo(false);
    }

    @Test
    void bidInTheLastSecondsExtendsTheAuction() {
        LocalDateTime end = LocalDateTime.now().plusSeconds(20);
        Auction auction = data.liveAuction(data.artisan(), "10", end);
        Map<String, Object> result = auctionService.placeBid(auction.getId(), 10, data.customer());

        assertThat(result.get("extended")).isEqualTo(true);
        assertThat(auctionRepository.findById(auction.getId()).orElseThrow().getEndTime()).isAfter(end.plusMinutes(1));
    }
}
