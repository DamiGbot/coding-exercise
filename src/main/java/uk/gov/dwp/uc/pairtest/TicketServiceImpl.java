package uk.gov.dwp.uc.pairtest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;
import uk.gov.dwp.uc.pairtest.validation.TicketPurchaseValidator;

/**
 * Implementation of {@link TicketService} that validates requests, calculates
 * payment and seat totals, then delegates to the external payment and reservation services.
 */
public class TicketServiceImpl implements TicketService {

    private static final Logger LOG = LoggerFactory.getLogger(TicketServiceImpl.class);

    private static final int ADULT_PRICE = 25;
    private static final int CHILD_PRICE = 15;

    private final TicketPaymentService paymentService;
    private final SeatReservationService reservationService;
    private final TicketPurchaseValidator validator;

    /**
     * Constructs a {@code TicketServiceImpl} with its required dependencies.
     *
     * @param paymentService     external service used to process payments.
     * @param reservationService external service used to reserve seats.
     * @param validator          enforces business rules before any processing occurs.
     */
    public TicketServiceImpl(TicketPaymentService paymentService,
                             SeatReservationService reservationService,
                             TicketPurchaseValidator validator) {
        this.paymentService = paymentService;
        this.reservationService = reservationService;
        this.validator = validator;
    }

    /**
     * Processes a ticket purchase for the given account.
     *
     * <p>Should only have private methods other than the one below.
     *
     * @param accountId          the purchasing account; must be greater than zero.
     * @param ticketTypeRequests one or more ticket requests describing type and quantity.
     * @throws InvalidPurchaseException if the request violates any business rule.
     */
    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests) throws InvalidPurchaseException {
        try {
            validator.validate(accountId, ticketTypeRequests);
        } catch (InvalidPurchaseException e) {
            LOG.warn("Purchase rejected for account {}: {}", accountId, e.getMessage());
            throw e;
        }

        int adultCount = 0;
        int childCount = 0;

        for (TicketTypeRequest request : ticketTypeRequests) {
            switch (request.getTicketType()) {
                case ADULT -> adultCount += request.getNoOfTickets();
                case CHILD -> childCount += request.getNoOfTickets();
                case INFANT -> { /* infants pay nothing and occupy no seat */ }
            }
        }

        int totalAmount = (adultCount * ADULT_PRICE) + (childCount * CHILD_PRICE);
        int seatsToReserve = adultCount + childCount;

        paymentService.makePayment(accountId, totalAmount);
        reservationService.reserveSeat(accountId, seatsToReserve);

        LOG.info("Purchase successful for account {}: charged £{}, reserved {} seat(s)",
                accountId, totalAmount, seatsToReserve);
    }
}