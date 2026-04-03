package uk.gov.dwp.uc.pairtest.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.ADULT;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.CHILD;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.INFANT;

/**
 * Unit tests for {@link TicketPurchaseValidator}.
 *
 * <p>Tests are grouped into four areas:
 * <ul>
 *   <li>Account ID validation</li>
 *   <li>Request array structure validation</li>
 *   <li>Ticket count and business rule validation</li>
 *   <li>Happy path scenarios</li>
 * </ul>
 *
 * <p>No mocks are required; {@link TicketPurchaseValidator} is a pure logic class
 * with no external dependencies.
 */
@DisplayName("TicketPurchaseValidator")
class TicketPurchaseValidatorTest {

    private TicketPurchaseValidator validator;

    /**
     * Creates a fresh {@link TicketPurchaseValidator} instance before each test.
     */
    @BeforeEach
    void setUp() {
        validator = new TicketPurchaseValidator();
    }

    // --- Account ID validation ---

    /**
     * Verifies that a null account ID is rejected.
     */
    @Test
    @DisplayName("should reject purchase when account ID is null")
    void should_throwException_when_accountIdIsNull() {
        assertThatThrownBy(() ->
                validator.validate(null, new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 1)}))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("Account ID must be greater than zero");
    }

    /**
     * Verifies that an account ID of zero is rejected, as only IDs greater than zero are valid.
     */
    @Test
    @DisplayName("should reject purchase when account ID is zero")
    void should_throwException_when_accountIdIsZero() {
        assertThatThrownBy(() ->
                validator.validate(0L, new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 1)}))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("Account ID must be greater than zero");
    }

    /**
     * Verifies that a negative account ID is rejected.
     */
    @Test
    @DisplayName("should reject purchase when account ID is negative")
    void should_throwException_when_accountIdIsNegative() {
        assertThatThrownBy(() ->
                validator.validate(-1L, new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 1)}))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("Account ID must be greater than zero");
    }

    // --- Request array validation ---

    /**
     * Verifies that a null ticket request array is rejected.
     */
    @Test
    @DisplayName("should reject purchase when request array is null")
    void should_throwException_when_requestArrayIsNull() {
        assertThatThrownBy(() ->
                validator.validate(1L, null))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("Ticket requests must not be null");
    }

    /**
     * Verifies that a zero-length ticket request array is rejected.
     * Calling {@code purchaseTickets(accountId)} with no varargs produces an empty array, not null.
     */
    @Test
    @DisplayName("should reject purchase when request array is empty")
    void should_throwException_when_requestArrayIsEmpty() {
        assertThatThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{}))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("At least one ticket request must be provided");
    }

    /**
     * Verifies that a request array containing a null element is rejected.
     */
    @Test
    @DisplayName("should reject purchase when request array contains a null element")
    void should_throwException_when_requestArrayContainsNull() {
        assertThatThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{null, new TicketTypeRequest(ADULT, 1)}))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("Individual ticket requests must not be null");
    }

    /**
     * Verifies that a ticket request with a quantity of zero is rejected.
     * A zero-quantity entry is not silently ignored to prevent it masking downstream rule violations.
     */
    @Test
    @DisplayName("should reject purchase when a ticket quantity is zero")
    void should_throwException_when_ticketQuantityIsZero() {
        assertThatThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 0)}))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("Ticket quantity must be greater than zero");
    }

    /**
     * Verifies that a ticket request with a negative quantity is rejected,
     * as a negative ticket count has no valid business meaning.
     */
    @Test
    @DisplayName("should reject purchase when a ticket quantity is negative")
    void should_throwException_when_ticketQuantityIsNegative() {
        assertThatThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{new TicketTypeRequest(ADULT, -1)}))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("Ticket quantity must be greater than zero");
    }

    // --- Ticket count validation ---

    /**
     * Verifies the upper boundary: a total of 26 tickets exceeds the maximum of 25 and is rejected.
     */
    @Test
    @DisplayName("should reject purchase when total ticket count exceeds 25")
    void should_throwException_when_totalTicketsIs26() {
        assertThatThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 26)}))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("Total ticket count exceeds maximum of 25");
    }

    /**
     * Verifies the upper boundary: a total of exactly 25 tickets is accepted.
     */
    @Test
    @DisplayName("should accept purchase when total ticket count is exactly 25")
    void should_pass_when_totalTicketsIs25() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 25)}));
    }

    /**
     * Verifies that a total of 24 tickets is accepted (one below the maximum).
     */
    @Test
    @DisplayName("should accept purchase when total ticket count is 24")
    void should_pass_when_totalTicketsIs24() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 24)}));
    }

    // --- Adult requirement validation ---

    /**
     * Verifies that Child tickets cannot be purchased without at least one Adult ticket.
     */
    @Test
    @DisplayName("should reject purchase when child tickets are requested without an adult")
    void should_throwException_when_childTicketsWithNoAdult() {
        assertThatThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{new TicketTypeRequest(CHILD, 2)}))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("Child tickets cannot be purchased without at least one Adult ticket");
    }

    /**
     * Verifies that Infant tickets cannot be purchased without at least one Adult ticket.
     */
    @Test
    @DisplayName("should reject purchase when infant tickets are requested without an adult")
    void should_throwException_when_infantTicketsWithNoAdult() {
        assertThatThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{new TicketTypeRequest(INFANT, 1)}))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("Infant tickets cannot be purchased without at least one Adult ticket");
    }

    /**
     * Verifies the infant-to-adult ratio rule: each infant sits on one adult's lap,
     * so the number of infants must not exceed the number of adults.
     */
    @Test
    @DisplayName("should reject purchase when infants outnumber adults")
    void should_throwException_when_infantsOutnumberAdults() {
        assertThatThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{
                        new TicketTypeRequest(ADULT, 1),
                        new TicketTypeRequest(INFANT, 2)
                }))
                .isInstanceOf(InvalidPurchaseException.class)
                .hasMessageContaining("Number of infants");
    }

    // --- Happy path ---

    /**
     * Verifies that a valid Adult-only request passes all validation rules.
     */
    @Test
    @DisplayName("should accept a valid adult-only purchase request")
    void should_pass_when_validAdultOnlyRequest() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 3)}));
    }

    /**
     * Verifies that a valid request containing all three ticket types passes all validation rules.
     */
    @Test
    @DisplayName("should accept a valid purchase with adult, child, and infant tickets")
    void should_pass_when_validAdultChildInfantRequest() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{
                        new TicketTypeRequest(ADULT, 2),
                        new TicketTypeRequest(CHILD, 1),
                        new TicketTypeRequest(INFANT, 2)
                }));
    }

    /**
     * Verifies the infant-to-adult ratio boundary: a request where infants exactly
     * equal the adult count is valid (one infant per lap).
     */
    @Test
    @DisplayName("should accept purchase when infant count equals adult count")
    void should_pass_when_infantCountEqualsAdultCount() {
        assertThatNoException().isThrownBy(() ->
                validator.validate(1L, new TicketTypeRequest[]{
                        new TicketTypeRequest(ADULT, 3),
                        new TicketTypeRequest(INFANT, 3)
                }));
    }
}