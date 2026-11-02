package com.bookstore.ecommerce.shipping;

import com.bookstore.ecommerce.catalog.CatalogService;
import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.Caller;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.config.AppProperties;
import com.bookstore.ecommerce.member.MemberAddressService;
import com.bookstore.ecommerce.shipping.ShippingDtos.DeliveryEstimate;
import com.bookstore.ecommerce.shipping.ShippingDtos.ShippingRateQuote;
import com.bookstore.ecommerce.store.StoreService;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipping")
@Validated
class ShippingController {

    private final ShippingRateService shippingRates;
    private final StoreService stores;
    private final CatalogService catalog;
    private final MemberAddressService addresses;
    private final AppProperties properties;
    private final Clock clock;

    ShippingController(
            ShippingRateService shippingRates,
            StoreService stores,
            CatalogService catalog,
            MemberAddressService addresses,
            AppProperties properties,
            Clock clock) {
        this.shippingRates = shippingRates;
        this.stores = stores;
        this.catalog = catalog;
        this.addresses = addresses;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping("/rates")
    public ShippingRateQuote getShippingRate(
            @RequestParam(required = false) UUID storeId,
            @RequestParam @NotNull @Pattern(regexp = "^[A-Z]{2}$") String country,
            @RequestParam @NotNull @Min(1) Integer weightGrams,
            @RequestParam(required = false) @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal orderSubtotal) {
        UUID store = stores.resolveStoreId(storeId);
        var quote = shippingRates.quote(store, country, weightGrams, orderSubtotal)
                .orElseThrow(() -> ApiException.of(ErrorCode.SHIPPING_UNAVAILABLE,
                        "No shipping rate available for the given destination country and weight."));

        return new ShippingRateQuote(
                country,
                weightGrams,
                properties.currency(),
                quote.band().rate(),
                quote.rate(),
                quote.freeShippingApplied(),
                quote.freeShippingThreshold(),
                quote.band().estimatedDaysMin(),
                quote.band().estimatedDaysMax());
    }

    @GetMapping("/estimate")
    public DeliveryEstimate estimateDelivery(
            Caller caller,
            @RequestParam(required = false) UUID storeId,
            @RequestParam @NotNull UUID productId,
            @RequestParam(required = false) UUID addressId,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Z]{2}$") String country) {
        if (addressId != null && country != null) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "addressId and country are mutually exclusive.");
        }
        if (addressId != null && caller.isGuest()) {
            throw ApiException.of(ErrorCode.UNAUTHENTICATED, "Authentication required when querying by addressId.");
        }

        String destinationCountry;
        if (addressId != null) {
            destinationCountry = addresses.findOwnAddress(caller.userId(), addressId)
                    .orElseThrow(() -> ApiException.of(ErrorCode.ADDRESS_NOT_FOUND, "Address not found: " + addressId))
                    .country();
        } else if (country != null) {
            destinationCountry = country;
        } else if (!caller.isGuest() && addresses.defaultAddressCountry(caller.userId()).isPresent()) {
            destinationCountry = addresses.defaultAddressCountry(caller.userId()).get();
        } else {
            destinationCountry = properties.defaultCountry();
        }

        UUID store = stores.resolveStoreId(storeId);
        var product = catalog.findVisible(productId, store, caller)
                .orElseThrow(() -> ApiException.of(ErrorCode.PRODUCT_NOT_FOUND, "Product not found: " + productId));

        if (!product.physical() || product.weightGrams() == null) {
            return new DeliveryEstimate(productId, destinationCountry, false, null, null, null);
        }

        var band = shippingRates.findBand(store, destinationCountry, product.weightGrams());
        if (band.isEmpty()) {
            return new DeliveryEstimate(productId, destinationCountry, false, null, null, null);
        }

        LocalDate tentativeDate = LocalDate.now(clock).plusDays(band.get().estimatedDaysMax());
        return new DeliveryEstimate(
                productId,
                destinationCountry,
                true,
                band.get().estimatedDaysMin(),
                band.get().estimatedDaysMax(),
                tentativeDate);
    }
}
