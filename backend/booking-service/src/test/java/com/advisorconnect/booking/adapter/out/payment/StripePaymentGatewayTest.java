package com.advisorconnect.booking.adapter.out.payment;

import com.advisorconnect.booking.domain.model.PaymentIntentResult;
import com.advisorconnect.booking.domain.port.out.PaymentGatewayException;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
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
 * The Stripe adapter in isolation: does it turn domain arguments into the right Stripe request,
 * and the Stripe response back into a domain object?
 *
 * <p>Possible only because {@code StripePaymentGateway} goes through the {@link StripeClient}
 * seam rather than calling the static {@code PaymentIntent.create}. No network, no static mocks.
 */
@ExtendWith(MockitoExtension.class)
class StripePaymentGatewayTest {

    @Mock
    private StripeClient stripeClient;

    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        gateway = new StripePaymentGateway(stripeClient);
    }

    // ------------------------------------------------------------------- amount conversion

    @ParameterizedTest(name = "${0} is sent to Stripe as {1}")
    @CsvSource({
            "50.00,  5000",   // the 30-minute product
            "90.00,  9000",   // the 60-minute product
            "0.99,     99",
            "1,       100",   // a BigDecimal with scale 0 must still scale up
            "1234.56, 123456"
    })
    @DisplayName("amounts are converted from dollars to whole cents")
    void amountsAreConvertedToMinorUnits(String dollars, long expectedCents) throws Exception {
        givenStripeReturns("pi_1", "pi_1_secret_abc");

        gateway.createPaymentIntent(new BigDecimal(dollars), "usd", Map.of());

        assertThat(capturedParams().getAmount()).isEqualTo(expectedCents);
    }

    @Test
    @DisplayName("a sub-cent amount is rounded rather than silently truncated")
    void subCentAmountsAreRounded() throws Exception {
        givenStripeReturns("pi_1", "pi_1_secret_abc");

        gateway.createPaymentIntent(new BigDecimal("50.005"), "usd", Map.of());

        assertThat(capturedParams().getAmount()).isEqualTo(5001L);
    }

    // --------------------------------------------------------------------- request contents

    @Test
    @DisplayName("currency and metadata are passed through to Stripe unchanged")
    void currencyAndMetadataArePassedThrough() throws Exception {
        givenStripeReturns("pi_1", "pi_1_secret_abc");
        Map<String, String> metadata = Map.of(
                "userId", "3f7c1c4e-2b8a-4a1d-9f2e-5c6d7e8f9a0b",
                "advisorId", "8a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

        gateway.createPaymentIntent(new BigDecimal("50.00"), "usd", metadata);

        PaymentIntentCreateParams params = capturedParams();
        assertThat(params.getCurrency()).isEqualTo("usd");
        assertThat(params.getMetadata()).containsAllEntriesOf(metadata);
    }

    @Test
    @DisplayName("automatic payment methods are enabled — Stripe rejects an intent with neither "
            + "that nor an explicit payment_method_types list")
    void automaticPaymentMethodsAreEnabled() throws Exception {
        givenStripeReturns("pi_1", "pi_1_secret_abc");

        gateway.createPaymentIntent(new BigDecimal("50.00"), "usd", Map.of());

        assertThat(capturedParams().getAutomaticPaymentMethods()).isNotNull();
        assertThat(capturedParams().getAutomaticPaymentMethods().getEnabled()).isTrue();
    }

    // -------------------------------------------------------------------- response mapping

    @Test
    @DisplayName("Stripe's PaymentIntent is mapped onto PaymentIntentResult")
    void responseIsMappedToDomainResult() throws Exception {
        givenStripeReturns("pi_3Nx9aB2eZvKYlo2C", "pi_3Nx9aB2eZvKYlo2C_secret_xyz789");

        PaymentIntentResult result =
                gateway.createPaymentIntent(new BigDecimal("90.00"), "usd", Map.of());

        assertThat(result).isEqualTo(new PaymentIntentResult(
                "pi_3Nx9aB2eZvKYlo2C", "pi_3Nx9aB2eZvKYlo2C_secret_xyz789"));
    }

    // ------------------------------------------------------------------------- failure path

    @Test
    @DisplayName("a StripeException becomes a PaymentGatewayException, so callers never see com.stripe.*")
    void stripeFailuresAreTranslated() throws Exception {
        willThrow(new ApiConnectionException("Stripe is unreachable"))
                .given(stripeClient).createPaymentIntent(any());

        assertThatThrownBy(() ->
                gateway.createPaymentIntent(new BigDecimal("50.00"), "usd", Map.of()))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Could not create payment intent")
                .hasCauseInstanceOf(ApiConnectionException.class);
    }

    // ------------------------------------------------------------------------------ helpers

    private void givenStripeReturns(String id, String clientSecret) throws Exception {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.setClientSecret(clientSecret);
        given(stripeClient.createPaymentIntent(any())).willReturn(intent);
    }

    private PaymentIntentCreateParams capturedParams() throws Exception {
        ArgumentCaptor<PaymentIntentCreateParams> captor =
                ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
        verify(stripeClient).createPaymentIntent(captor.capture());
        return captor.getValue();
    }
}
