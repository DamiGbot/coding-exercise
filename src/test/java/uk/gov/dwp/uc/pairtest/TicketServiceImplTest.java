package uk.gov.dwp.uc.pairtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;
import uk.gov.dwp.uc.pairtest.validation.TicketPurchaseValidator;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.ADULT;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.CHILD;
import static uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type.INFANT;

/**
 * Unit tests for {@link TicketServiceImpl}, covering happy paths,
 * parameterised calculations, and validation delegation.
 */
@DisplayName("TicketServiceImpl")
@ExtendWith(MockitoExtension.class)
class TicketServiceImplTest {

    @Mock
    private TicketPaymentService paymentService;

    @Mock
    private SeatReservationService reservationService;

    @Mock
    private TicketPurchaseValidator validator;

    private TicketServiceImpl ticketService;

    /**
     * Sets up the mocks and creates a new {@link TicketServiceImpl} instance before each test runs.
     */
    @BeforeEach
    void setUp() {
        ticketService = new TicketServiceImpl(paymentService, reservationService, validator);
    }

    // --- Happy path tests ---

    /**
     * Verifies correct payment and seat count when only Adult tickets are purchased.
     */
    @Test
    @DisplayName("should charge £50 and reserve 2 seats for 2 adult tickets")
    void should_chargeCorrectAmountAndReserveCorrectSeats_when_adultOnly() {
        ticketService.purchaseTickets(1L, new TicketTypeRequest(ADULT, 2));

        verify(paymentService).makePayment(1L, 50);
        verify(reservationService).reserveSeat(1L, 2);
    }

    /**
     * Verifies correct payment and seat count when Adult and Child tickets are purchased.
     */
    @Test
    @DisplayName("should charge £55 and reserve 3 seats for 1 adult and 2 child tickets")
    void should_chargeCorrectAmountAndReserveCorrectSeats_when_adultAndChild() {
        ticketService.purchaseTickets(1L,
                new TicketTypeRequest(ADULT, 1),
                new TicketTypeRequest(CHILD, 2));

        verify(paymentService).makePayment(1L, 55);
        verify(reservationService).reserveSeat(1L, 3);
    }

    /**
     * Verifies correct payment and seat count when Adult and Infant tickets are purchased.
     * Infants are not charged and do not occupy a seat.
     */
    @Test
    @DisplayName("should charge adults only and reserve no seats for infants")
    void should_chargeCorrectAmountAndReserveCorrectSeats_when_adultAndInfant() {
        ticketService.purchaseTickets(1L,
                new TicketTypeRequest(ADULT, 2),
                new TicketTypeRequest(INFANT, 2));

        verify(paymentService).makePayment(1L, 50);
        verify(reservationService).reserveSeat(1L, 2);
    }

    /**
     * Verifies correct payment and seat count when all three ticket types are purchased.
     */
    @Test
    @DisplayName("should calculate correct totals when all three ticket types are purchased")
    void should_chargeCorrectAmountAndReserveCorrectSeats_when_allThreeTypes() {
        ticketService.purchaseTickets(1L,
                new TicketTypeRequest(ADULT, 2),
                new TicketTypeRequest(CHILD, 1),
                new TicketTypeRequest(INFANT, 1));

        verify(paymentService).makePayment(1L, 65);
        verify(reservationService).reserveSeat(1L, 3);
    }

    /**
     * Verifies that a purchase at the maximum allowed limit of 25 tickets is processed correctly.
     */
    @Test
    @DisplayName("should process a purchase at the maximum limit of 25 tickets")
    void should_chargeCorrectAmountAndReserveCorrectSeats_when_maximumTickets() {
        ticketService.purchaseTickets(1L,
                new TicketTypeRequest(ADULT, 15),
                new TicketTypeRequest(CHILD, 5),
                new TicketTypeRequest(INFANT, 5));

        verify(paymentService).makePayment(1L, 450);
        verify(reservationService).reserveSeat(1L, 20);
    }

    /**
     * Verifies that multiple requests of the same ticket type are summed before processing.
     */
    @Test
    @DisplayName("should sum duplicate ticket types before calculating payment and seats")
    void should_sumDuplicateTypes_when_sameTypeAppearsMoreThanOnce() {
        ticketService.purchaseTickets(1L,
                new TicketTypeRequest(ADULT, 2),
                new TicketTypeRequest(ADULT, 3));

        verify(paymentService).makePayment(1L, 125);
        verify(reservationService).reserveSeat(1L, 5);
    }

    // --- Parameterised calculation tests ---

    /**
     * Provides ticket combinations with their expected payment amount and seat count.
     *
     * @return a stream of arguments: adults, children, infants, expectedPayment, expectedSeats.
     */
    static Stream<Arguments> ticketCombinations() {
        return Stream.of(
                // adults, children, infants, expectedPayment, expectedSeats
                Arguments.of(1, 0, 0,  25,  1),
                Arguments.of(3, 0, 0,  75,  3),
                Arguments.of(1, 1, 0,  40,  2),
                Arguments.of(2, 3, 0,  95,  5),
                Arguments.of(1, 0, 1,  25,  1),
                Arguments.of(3, 2, 3, 105,  5),
                Arguments.of(5, 5, 5, 200, 10)
        );
    }

    /**
     * Verifies the correct payment amount and seat count across multiple ticket combinations.
     *
     * @param adults          number of Adult tickets.
     * @param children        number of Child tickets.
     * @param infants         number of Infant tickets.
     * @param expectedPayment expected total payment in pence.
     * @param expectedSeats   expected number of seats to reserve.
     */
    @ParameterizedTest(name = "{0} adults, {1} children, {2} infants → £{3}, {4} seats")
    @MethodSource("ticketCombinations")
    @DisplayName("should calculate correct payment and seat count for various ticket combinations")
    void should_calculateCorrectPaymentAndSeats(
            int adults, int children, int infants, int expectedPayment, int expectedSeats) {

        TicketTypeRequest[] requests = buildRequests(adults, children, infants);
        ticketService.purchaseTickets(1L, requests);

        verify(paymentService).makePayment(1L, expectedPayment);
        verify(reservationService).reserveSeat(1L, expectedSeats);
    }

    // --- Validation delegation tests ---

    /**
     * Verifies that external services are never called when validation fails.
     */
    @Test
    @DisplayName("should not call external services when validation fails")
    void should_neverCallExternalServices_when_validationFails() {
        doThrow(new InvalidPurchaseException("invalid"))
                .when(validator).validate(anyLong(), any(TicketTypeRequest[].class));

        assertThatThrownBy(() -> ticketService.purchaseTickets(0L, new TicketTypeRequest(ADULT, 1)))
                .isInstanceOf(InvalidPurchaseException.class);

        verifyNoInteractions(paymentService, reservationService);
    }

    /**
     * Verifies that {@code purchaseTickets} delegates validation to the {@link TicketPurchaseValidator}.
     */
    @Test
    @DisplayName("should delegate validation to TicketPurchaseValidator before processing")
    void should_delegateValidationToValidator_when_purchaseTicketsIsCalled() {
        TicketTypeRequest request = new TicketTypeRequest(ADULT, 1);
        ticketService.purchaseTickets(1L, request);

        verify(validator).validate(1L, new TicketTypeRequest[]{request});
    }

    // --- Helpers ---

    /**
     * Builds a {@link TicketTypeRequest} array from the given counts, omitting zero-quantity types.
     *
     * @param adults   number of Adult tickets.
     * @param children number of Child tickets.
     * @param infants  number of Infant tickets.
     * @return an array of ticket requests containing only non-zero entries.
     */
    private TicketTypeRequest[] buildRequests(int adults, int children, int infants) {
        int count = (adults > 0 ? 1 : 0) + (children > 0 ? 1 : 0) + (infants > 0 ? 1 : 0);
        TicketTypeRequest[] requests = new TicketTypeRequest[count];
        int i = 0;
        if (adults   > 0) requests[i++] = new TicketTypeRequest(ADULT,  adults);
        if (children > 0) requests[i++] = new TicketTypeRequest(CHILD,  children);
        if (infants  > 0) requests[i]   = new TicketTypeRequest(INFANT, infants);
        return requests;
    }
}