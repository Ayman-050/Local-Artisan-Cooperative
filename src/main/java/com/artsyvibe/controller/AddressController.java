package com.artsyvibe.controller;

import com.artsyvibe.model.Address;
import com.artsyvibe.model.User;
import com.artsyvibe.repository.AddressRepository;
import com.artsyvibe.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * The signed-in user's saved addresses. Addresses belong to whoever is in the session; the {@code email}
 * query parameter / body field the front end still sends is ignored.
 */
@RestController
@RequestMapping("/api/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressRepository addressRepository;
    private final CurrentUser currentUser;

    @GetMapping
    public ResponseEntity<List<Address>> getAddresses() {
        return ResponseEntity.ok(addressRepository.findByUserEmail(currentUser.require().getEmail()));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Address> createAddress(@RequestBody Address body) {
        User user = currentUser.require();
        Address address = new Address();
        copyFields(body, address);
        address.setUserEmail(user.getEmail());
        if (address.isDefault()) {
            clearDefaults(user.getEmail());
        }
        return ResponseEntity.ok(addressRepository.save(address));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> updateAddress(@PathVariable Long id, @RequestBody Address body) {
        Address address = findOwned(id);
        if (body.isDefault()) {
            clearDefaults(address.getUserEmail());
        }
        copyFields(body, address);
        return ResponseEntity.ok(addressRepository.save(address));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Boolean>> deleteAddress(@PathVariable Long id) {
        addressRepository.delete(findOwned(id));
        return ResponseEntity.ok(Map.of("success", true));
    }

    private Address findOwned(Long id) {
        String email = currentUser.require().getEmail();
        return addressRepository.findById(id)
                .filter(a -> email != null && email.equalsIgnoreCase(a.getUserEmail()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    private static void copyFields(Address from, Address to) {
        to.setLabel(limit(from.getLabel(), 255, "Label"));
        to.setStreet(required(limit(from.getStreet(), 500, "Street"), "Street"));
        to.setCity(required(limit(from.getCity(), 255, "City"), "City"));
        to.setZip(limit(from.getZip(), 255, "ZIP code"));
        to.setCountry(required(limit(from.getCountry(), 255, "Country"), "Country"));
        to.setDefault(from.isDefault());
    }

    private static String limit(String value, int max, String label) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if (v.length() > max) {
            throw new IllegalArgumentException(label + " is too long.");
        }
        return v;
    }

    private static String required(String value, String label) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value;
    }

    private void clearDefaults(String userEmail) {
        addressRepository.findByUserEmail(userEmail).forEach(a -> a.setDefault(false));
    }
}
