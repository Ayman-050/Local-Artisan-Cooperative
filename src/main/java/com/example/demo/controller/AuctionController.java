package com.example.demo.controller;

import com.example.demo.model.Customer;
import com.example.demo.security.CurrentUser;
import com.example.demo.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;
    private final CurrentUser currentUser;

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getAuction(@PathVariable Long id) {
        return auctionService.findById(id)
                .map(auction -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "auction", auctionService.toDetailMap(auction))))
                .orElse(ResponseEntity.notFound().build());
    }

    /** The bidder is always the signed-in customer; any bidderId/bidderName in the body is ignored. */
    @PostMapping("/{id}/bid")
    public ResponseEntity<?> placeBid(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Customer bidder = currentUser.requireCustomer();

        Object amountObj = body != null ? body.get("amount") : null;
        if (amountObj == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Bid amount is required."));
        }
        int bidAmount;
        try {
            BigDecimal parsed = new BigDecimal(String.valueOf(amountObj).trim());
            if (parsed.stripTrailingZeros().scale() > 0) {
                return ResponseEntity.badRequest().body(Map.of("success", false,
                        "message", "Bids must be whole BD amounts only (no decimals)."));
            }
            bidAmount = parsed.intValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false,
                    "message", "Please enter a valid bid amount."));
        }

        Map<String, Object> result = auctionService.placeBid(id, bidAmount, bidder);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }
}
