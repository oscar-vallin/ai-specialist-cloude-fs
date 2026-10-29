package com.bookstore.ecommerce.member;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.member.MemberDtos.Address;
import com.bookstore.ecommerce.member.MemberDtos.AddressRequest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AddressService implements MemberAddressService {

    private final MemberRepository repository;

    AddressService(MemberRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    List<Address> list(UUID userId) {
        return repository.listAddresses(userId);
    }

    @Transactional(readOnly = true)
    Address get(UUID userId, UUID addressId) {
        return repository.findAddress(userId, addressId).orElseThrow(AddressService::notFound);
    }

    @Transactional
    Address create(UUID userId, AddressRequest request) {
        boolean first = repository.countAddresses(userId) == 0;
        boolean makeDefault = first || Boolean.TRUE.equals(request.isDefault());
        if (makeDefault) {
            repository.clearDefault(userId);
        }
        UUID id = repository.insertAddress(userId, request, makeDefault);
        return repository.findAddress(userId, id).orElseThrow();
    }

    @Transactional
    Address update(UUID userId, UUID addressId, AddressRequest request) {
        Address current = get(userId, addressId);
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault());
        // Full replacement: omitting isDefault keeps an existing default (it can only be changed through the default endpoint).
        boolean isDefault = makeDefault || current.isDefault();
        if (makeDefault) {
            repository.clearDefault(userId);
        }
        repository.updateAddress(addressId, request, isDefault);
        return repository.findAddress(userId, addressId).orElseThrow();
    }

    @Transactional
    void delete(UUID userId, UUID addressId) {
        get(userId, addressId);
        repository.deleteAddress(addressId);
    }

    @Transactional
    Address setDefault(UUID userId, UUID addressId) {
        get(userId, addressId);
        repository.clearDefault(userId);
        repository.setDefault(addressId);
        return repository.findAddress(userId, addressId).orElseThrow();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Address> findOwnAddress(UUID userId, UUID addressId) {
        return repository.findAddress(userId, addressId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> defaultAddressCountry(UUID userId) {
        return repository.defaultAddressCountry(userId);
    }

    private static ApiException notFound() {
        return ApiException.of(ErrorCode.ADDRESS_NOT_FOUND, "The address does not exist.");
    }
}
