package com.advisorconnect.booking.adapter.out.payment;

import com.advisorconnect.booking.domain.model.PaymentOrderResult;
import com.advisorconnect.booking.domain.port.out.PaymentGatewayException;
import com.razorpay.Order;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

/**
 * The Razorpay adapter in isolation: does it turn domain arguments into the right Razorpay
 * request, and the Razorpay response back into a domain object?
 *
 * <p>Possible only because {@code RazorpayPaymentGateway} goes through the
 * {@link RazorpayOrderClient} seam rather than constructing an {@code OrderClient} directly.
 * No network involved.
 */
@ExtendWith(MockitoExtension.class)
class RazorpayPaymentGatewayTest {

    @Mock
    private RazorpayOrderClient razorpayOrderClient;

    private RazorpayPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new RazorpayPaymentGateway(razorpayOrderClient);
    }

    // ------------------------------------------------------------------- amount conversion

    @ParameterizedTest(name = "₹{0} is sent to Razorpay as {1} paise")
    @CsvSource({
            "500.00,  50000",   // the 30-minute product
            "900.00,  90000",   // the 60-minute product
            "0.99,       99",
            "1,         100",   // a BigDecimal with scale 0 must still scale up
            "1234.56, 123456"
    })
    @DisplayName("amounts are converted from rupees to whole paise")
    void amountsAreConvertedToMinorUnits(String rupees, long expectedPaise) throws Exception {
        givenRazorpayReturns("order_1");

        gateway.createOrder(new BigDecimal(rupees), "INR", Map.of());

        assertThat(capturedParams().getLong("amount")).isEqualTo(expectedPaise);
    }

    @Test
    @DisplayName("a sub-paise amount is rounded rather than silently truncated")
    void subPaiseAmountsAreRounded() throws Exception {
        givenRazorpayReturns("order_1");

        gateway.createOrder(new BigDecimal("500.005"), "INR", Map.of());

        assertThat(capturedParams().getLong("amount")).isEqualTo(50001L);
    }

    // --------------------------------------------------------------------- request contents

    @Test
    @DisplayName("currency and metadata are passed through to Razorpay unchanged")
    void currencyAndMetadataArePassedThrough() throws Exception {
        givenRazorpayReturns("order_1");
        Map<String, String> metadata = Map.of(
                "userId", "3f7c1c4e-2b8a-4a1d-9f2e-5c6d7e8f9a0b",
                "advisorId", "8a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

        gateway.createOrder(new BigDecimal("500.00"), "INR", metadata);

        JSONObject params = capturedParams();
        assertThat(params.getString("currency")).isEqualTo("INR");
        JSONObject notes = params.getJSONObject("notes");
        assertThat(notes.getString("userId")).isEqualTo(metadata.get("userId"));
        assertThat(notes.getString("advisorId")).isEqualTo(metadata.get("advisorId"));
    }

    @Test
    @DisplayName("the order auto-captures — an authorised-but-uncaptured charge is money the "
            + "advisor never receives for a session the calendar still shows as held")
    void paymentCaptureIsEnabled() throws Exception {
        givenRazorpayReturns("order_1");

        gateway.createOrder(new BigDecimal("500.00"), "INR", Map.of());

        assertThat(capturedParams().getInt("payment_capture")).isEqualTo(1);
    }

    @Test
    @DisplayName("every request carries a unique receipt identifier")
    void receiptIsPresent() throws Exception {
        givenRazorpayReturns("order_1");

        gateway.createOrder(new BigDecimal("500.00"), "INR", Map.of());

        assertThat(capturedParams().getString("receipt")).isNotBlank();
    }

    // -------------------------------------------------------------------- response mapping

    @Test
    @DisplayName("Razorpay's Order is mapped onto PaymentOrderResult")
    void responseIsMappedToDomainResult() throws Exception {
        givenRazorpayReturns("order_DESlLckIVRkHWj");

        PaymentOrderResult result =
                gateway.createOrder(new BigDecimal("900.00"), "INR", Map.of());

        assertThat(result).isEqualTo(new PaymentOrderResult("order_DESlLckIVRkHWj"));
    }

    // ------------------------------------------------------------------------- failure path

    @Test
    @DisplayName("a RazorpayException becomes a PaymentGatewayException, so callers never see "
            + "com.razorpay.*")
    void razorpayFailuresAreTranslated() throws Exception {
        willThrow(new RazorpayException("Razorpay is unreachable"))
                .given(razorpayOrderClient).createOrder(any());

        assertThatThrownBy(() ->
                gateway.createOrder(new BigDecimal("500.00"), "INR", Map.of()))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Could not create payment order")
                .hasCauseInstanceOf(RazorpayException.class);
    }

    // ------------------------------------------------------------------------------ helpers

    private void givenRazorpayReturns(String orderId) throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", orderId);
        Order order = new Order(json);
        given(razorpayOrderClient.createOrder(any())).willReturn(order);
    }

    private JSONObject capturedParams() throws Exception {
        ArgumentCaptor<JSONObject> captor = ArgumentCaptor.forClass(JSONObject.class);
        verify(razorpayOrderClient).createOrder(captor.capture());
        return captor.getValue();
    }
}
