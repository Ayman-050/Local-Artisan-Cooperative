package com.example.demo.config;

import com.example.demo.service.AuctionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AuctionScheduleInitializer implements ApplicationRunner {

    @Autowired
    private AuctionService auctionService;

    @Override
    public void run(ApplicationArguments args) {
        auctionService.refreshStaleActiveAuctionWindows();
        auctionService.syncStoredStatuses();
    }
}
