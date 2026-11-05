package com.example.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public CommandLineRunner databaseSchemaUpdater(JdbcTemplate jdbcTemplate) {
		return args -> {
			try {
				jdbcTemplate.execute("ALTER TABLE products ALTER COLUMN image_url TYPE TEXT");
				System.out.println("Schema Migration: successfully altered products.image_url to TEXT");
			} catch (Exception ex) {
				System.err.println("Schema Migration: could not alter products.image_url column: " + ex.getMessage());
			}
			try {
				int updatedRows = jdbcTemplate.update("UPDATE products SET image_url = 'https://images.unsplash.com/photo-1579783900882-c0d3dad7b119?w=600' WHERE image_url LIKE 'data:image%' AND LENGTH(image_url) > 100000");
				System.out.println("Data Cleanup: successfully cleaned " + updatedRows + " bloated base64 images from database");
			} catch (Exception ex) {
				System.err.println("Data Cleanup: could not clean bloated base64 images: " + ex.getMessage());
			}
			try {
				jdbcTemplate.execute("ALTER TABLE users ALTER COLUMN profile_picture TYPE TEXT");
				System.out.println("Schema Migration: successfully altered users.profile_picture to TEXT");
			} catch (Exception ex) {
				System.err.println("Schema Migration: could not alter users.profile_picture column: " + ex.getMessage());
			}
			try {
				jdbcTemplate.execute("ALTER TABLE artisans ALTER COLUMN shop_banner TYPE TEXT");
				System.out.println("Schema Migration: successfully altered artisans.shop_banner to TEXT");
			} catch (Exception ex) {
				System.err.println("Schema Migration: could not alter artisans.shop_banner column: " + ex.getMessage());
			}
			try {
				jdbcTemplate.execute("ALTER TABLE order_items ADD COLUMN IF NOT EXISTS refund_requested BOOLEAN DEFAULT FALSE");
				jdbcTemplate.execute("ALTER TABLE order_items ADD COLUMN IF NOT EXISTS refund_status VARCHAR(255)");
				jdbcTemplate.execute("ALTER TABLE order_items ADD COLUMN IF NOT EXISTS refund_reason VARCHAR(4000)");
				jdbcTemplate.execute("ALTER TABLE order_items ADD COLUMN IF NOT EXISTS refund_images TEXT");
				jdbcTemplate.execute("ALTER TABLE order_items ADD COLUMN IF NOT EXISTS artisan_refusal_reason VARCHAR(4000)");
				jdbcTemplate.execute("ALTER TABLE order_items ADD COLUMN IF NOT EXISTS admin_refund_status VARCHAR(255) DEFAULT 'PENDING'");
				jdbcTemplate.execute("ALTER TABLE order_items ADD COLUMN IF NOT EXISTS admin_note VARCHAR(4000)");
				System.out.println("Schema Migration: successfully verified/added refund columns to order_items");
			} catch (Exception ex) {
				System.err.println("Schema Migration: could not alter order_items table for refund columns: " + ex.getMessage());
			}
		};
	}
}
