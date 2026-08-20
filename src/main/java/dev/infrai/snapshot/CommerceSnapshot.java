package dev.infrai.snapshot;

import java.time.Instant;
import java.time.LocalDate;

public record CommerceSnapshot(
        LocalDate businessDate,
        Instant capturedAt,
        Checkout checkout,
        Fulfillment fulfillment,
        Receipt receipt,
        CustomerOrderUpdate customerOrderUpdate) {

    public record Checkout(String checkoutId, String orderId, long authorizedMinorUnits, String currency) {}
    public record Fulfillment(String orderId, String status, String carrierReference) {}
    public record Receipt(String receiptId, String orderId, long settledMinorUnits) {}
    public record CustomerOrderUpdate(String orderId, long sequence, String customerVisibleStatus) {}

    public String toJson() {
        return "{" +
                field("business_date", businessDate.toString()) + "," +
                field("captured_at", capturedAt.toString()) + "," +
                "\"checkout\":{" + field("checkout_id", checkout.checkoutId()) + "," +
                field("order_id", checkout.orderId()) + ",\"authorized_minor_units\":" + checkout.authorizedMinorUnits() + "," +
                field("currency", checkout.currency()) + "}," +
                "\"fulfillment\":{" + field("order_id", fulfillment.orderId()) + "," +
                field("status", fulfillment.status()) + "," + field("carrier_reference", fulfillment.carrierReference()) + "}," +
                "\"receipt\":{" + field("receipt_id", receipt.receiptId()) + "," + field("order_id", receipt.orderId()) +
                ",\"settled_minor_units\":" + receipt.settledMinorUnits() + "}," +
                "\"customer_order_update\":{" + field("order_id", customerOrderUpdate.orderId()) +
                ",\"sequence\":" + customerOrderUpdate.sequence() + "," +
                field("customer_visible_status", customerOrderUpdate.customerVisibleStatus()) + "}}";
    }

    private static String field(String name, String value) {
        return "\"" + name + "\":\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
