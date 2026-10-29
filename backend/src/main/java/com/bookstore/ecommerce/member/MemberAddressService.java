package com.bookstore.ecommerce.member;

import com.bookstore.ecommerce.member.MemberDtos.Address;
import java.util.Optional;
import java.util.UUID;

/** What other domains may ask the member domain about delivery addresses. */
public interface MemberAddressService {

    /** The caller's own address, or empty when it does not exist or belongs to someone else. */
    Optional<Address> findOwnAddress(UUID userId, UUID addressId);

    /** Country of the user's default address, if the user has one. */
    Optional<String> defaultAddressCountry(UUID userId);
}
