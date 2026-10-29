package com.bookstore.ecommerce.member;

import java.util.UUID;

/** Implemented by the cart (order domain). Called at login to merge a guest cart into the user's cart (FR-O1). */
public interface GuestCartMerger {

    /** @return true when an active guest cart was found and merged */
    boolean merge(UUID userId, String guestToken);
}
