package com.artsyvibe.config;

import com.artsyvibe.service.AuctionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Keeps stored auction statuses (UPCOMING / LIVE / ENDED) in step with the clock and fires the
 * start/end notifications. Runs on one scheduler thread, so transitions are processed exactly once,
 * instead of on every page view as before.
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class AuctionScheduleInitializer implements ApplicationRunner {

    private final AuctionService auctionService;

    @Value("${app.demo.refresh-stale-auctions:false}")
    private boolean refreshStaleAuctions;

    @Override
    public void run(ApplicationArguments args) {
        if (refreshStaleAuctions) {
            log.warn("app.demo.refresh-stale-auctions=true: re-opening LIVE auctions whose end time has passed.");
            auctionService.refreshStaleActiveAuctionWindows();
        }
    }

    @Scheduled(fixedDelayString = "${app.auctions.sync-interval-ms:15000}", initialDelay = 2000)
    public void syncAuctionStatuses() {
        try {
            auctionService.syncStoredStatuses();
        } catch (RuntimeException ex) {
            log.error("Auction status sync failed", ex);
        }
    }
}
