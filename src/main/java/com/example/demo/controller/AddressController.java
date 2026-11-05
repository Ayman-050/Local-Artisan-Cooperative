package com.example.demo.controller;

import com.example.demo.model.Address;
import com.example.demo.repository.AddressRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/addresses")
public class AddressController {

    @Autowired
    private AddressRepository addressRepository;

    @GetMapping
    public ResponseEntity<List<Address>> getAddresses(@RequestParam String email) {
        return ResponseEntity.ok(addressRepository.findByUserEmail(email));
    }

    @PostMapping
    public ResponseEntity<Address> createAddress(@RequestBody Address address) {
        if (address.isDefault()) {
            clearDefaults(address.getUserEmail());
        }
        Address saved = addressRepository.save(address);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAddress(@PathVariable Long id, @RequestBody Address updated) {
        Optional<Address> opt = addressRepository.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Address address = opt.get();
        if (updated.isDefault()) {
            clearDefaults(address.getUserEmail());
        }
        address.setLabel(updated.getLabel());
        address.setStreet(updated.getStreet());
        address.setCity(updated.getCity());
        address.setZip(updated.getZip());
        address.setCountry(updated.getCountry());
        address.setDefault(updated.isDefault());
        return ResponseEntity.ok(addressRepository.save(address));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Boolean>> deleteAddress(@PathVariable Long id) {
        addressRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void clearDefaults(String userEmail) {
        addressRepository.findByUserEmail(userEmail).forEach(a -> {
            if (a.isDefault()) {
                a.setDefault(false);
                addressRepository.save(a);
            }
        });
    }
}
