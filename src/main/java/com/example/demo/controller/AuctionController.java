package com.example.demo.controller;

import com.example.demo.model.Auction;
import com.example.demo.service.AuctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auctions")
public class AuctionController {

    @Autowired
    private AuctionService auctionService;

    @GetMapping("/{id}")
    public ResponseEntity<?> getAuction(@PathVariable Long id) {
        try {
            auctionService.syncStoredStatuses();
        } catch (Exception ex) {
            // Ignore
        }
        return auctionService.findById(id)
                .map(auction -> ResponseEntity.ok(Map.of(
                        "success", true,
                        "auction", auctionService.toDetailMap(auction))))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/bid")
    public ResponseEntity<?> placeBid(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            auctionService.syncStoredStatuses();
        } catch (Exception ex) {
            // Ignore
        }
        Object amountObj = body != null ? body.get("amount") : null;
        String bidderName = body != null && body.get("bidderName") != null
                ? String.valueOf(body.get("bidderName")).trim()
                : "";

        if (amountObj == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Bid amount is required."));
        }

        int bidAmount;
        try {
            double parsed = amountObj instanceof Number
                    ? ((Number) amountObj).doubleValue()
                    : Double.parseDouble(String.valueOf(amountObj));
            if (parsed != Math.floor(parsed)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Bids must be whole BD amounts only (no decimals)."));
            }
            bidAmount = (int) parsed;
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Please enter a valid bid amount."));
        }

        Object bidderIdObj = body.get("bidderId");
        Long bidderId = bidderIdObj != null ? Long.parseLong(bidderIdObj.toString()) : null;

        Map<String, Object> result = auctionService.placeBid(id, bidAmount, bidderName, bidderId);
        boolean success = Boolean.TRUE.equals(result.get("success"));
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }
}
