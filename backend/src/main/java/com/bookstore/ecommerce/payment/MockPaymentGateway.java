package com.bookstore.ecommerce.payment;

import com.bookstore.ecommerce.common.ApiException;
import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.config.AppProperties;
import java.time.Clock;
import java.time.YearMonth;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Mock payment gateway implementation (FR-P1). */
@Component
class MockPaymentGateway implements PaymentGateway {

    private final AppProperties properties;
    private final Clock clock;

    MockPaymentGateway(AppProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public GatewayResponse authorizeAndCapture(ChargeRequest request) {
        if (!passesLuhn(request.cardNumber())) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Card number failed Luhn validation.");
        }

        YearMonth cardExpiry = YearMonth.of(request.expiryYear(), request.expiryMonth());
        YearMonth currentYearMonth = YearMonth.now(clock);
        if (cardExpiry.isBefore(currentYearMonth)) {
            throw ApiException.of(ErrorCode.VALIDATION_FAILED, "Card is expired.");
        }

        String declineCardNumber = properties.payment() != null ? properties.payment().mockDeclineCardNumber() : "4000000000000002";
        if (request.cardNumber().equals(declineCardNumber)) {
            return new GatewayResponse(false, null, "Card declined by issuer");
        }

        String reference = "mock_ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return new GatewayResponse(true, reference, null);
    }

    @Override
    public GatewayResponse refund(RefundRequest request) {
        String reference = "mock_rf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return new GatewayResponse(true, reference, null);
    }

    public static boolean passesLuhn(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return false;
        }
        int sum = 0;
        boolean alternate = false;
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int n = cardNumber.charAt(i) - '0';
            if (n < 0 || n > 9) {
                return false;
            }
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n = (n % 10) + 1;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (sum % 10 == 0);
    }

    public static String detectBrand(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return null;
        }
        if (cardNumber.startsWith("4")) {
            return "VISA";
        }
        if (cardNumber.matches("^(5[1-5]|222[1-9]|22[3-9]\\d|2[3-6]\\d{2}|27[01]\\d|2720).*")) {
            return "MASTERCARD";
        }
        if (cardNumber.startsWith("34") || cardNumber.startsWith("37")) {
            return "AMEX";
        }
        if (cardNumber.startsWith("6011") || cardNumber.startsWith("65") || cardNumber.matches("^64[4-9].*")) {
            return "DISCOVER";
        }
        return "OTHER";
    }
}
