---
name: checkout-flow
description: Use when the user wants to implement, modify or test the cart-to-order flow: checkout, payment with gift points and wallet, order confirmation, 48-hour cancellation, return and refund, or Buy Again.
---

# Checkout flow

1. Read `requirements.md` sections 4.4, 4.5, 4.6, 4.7 and `.bob/rules/security-and-payments.md`. Ensure the operations exist in `api/openapi.yaml` (otherwise use `openapi-first`).
2. Order lifecycle (the only allowed transitions):
   `CART -> PENDING_PAYMENT -> CONFIRMED -> SHIPPED -> DELIVERED`;
   `CONFIRMED -> CANCELLED` (inside the window); `DELIVERED -> RETURN_REQUESTED -> RETURNED`; `PENDING_PAYMENT -> PAYMENT_FAILED`.
   Reject any other transition with a stable error code.
3. Checkout:
   - only authenticated `CUSTOMER` users; guests are rejected (401/403). A guest cart merges into the user cart on login;
   - validate address, stock and current prices on the server; recompute items, shipping (shipping domain service) and discounts (coupon, gift points);
   - require an `Idempotency-Key` on order placement and payment.
4. Payment via the gateway abstraction (mock implementation only). Support card (mock), wallet, gift points or a combination. Gift points convert at the configurable rate (default 100 points = 1 USD). Never store card data.
5. Confirmation: on successful payment set `CONFIRMED`, generate a confirmation number, create the shipment through the shipping service interface.
6. Cancel: allowed only within the window from the store policy (default 48h) measured from confirmation, using an injectable `Clock`. Outside the window return 409 or 422 with a clear error code. Cancelling triggers a refund.
7. Return (after `DELIVERED`): create a return shipment and a refund.
8. Refunds go back to the original method; gift points and wallet are credited back (ledger/transaction rows, never in-place balance edits only).
9. Buy Again: re-add past order items to the cart, validating current price and stock; report unavailable items.
10. Domains interact only through service interfaces (order -> payment, shipping, catalog, member).
11. Tests: cancel at 47h succeeds, after 48h fails; guests cannot check out; insufficient wallet/points; idempotent retry; refund credits back; Buy Again uses current price. Run with `./mvnw test` from `backend/`.
