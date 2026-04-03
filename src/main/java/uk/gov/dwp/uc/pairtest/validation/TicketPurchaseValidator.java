package uk.gov.dwp.uc.pairtest.validation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

/**
 * Validates ticket purchase requests against the cinema ticketing business rules.
 */
public class TicketPurchaseValidator {

    private static final Logger LOG = LoggerFactory.getLogger(TicketPurchaseValidator.class);

    private static final int MAX_TICKETS = 25;

    /**
     * Validates a ticket purchase request in full.
     *
     * @param accountId          the account making the purchase; must be greater than zero.
     * @param ticketTypeRequests the ticket requests to validate; must not be null or empty.
     * @throws InvalidPurchaseException if any validation rule is violated.
     */
    public void validate(Long accountId, TicketTypeRequest[] ticketTypeRequests) {
        validateAccountId(accountId);
        validateRequestsArray(ticketTypeRequests);
        validateTicketCounts(ticketTypeRequests);
    }

    /**
     * Validates that the account ID is non-null and greater than zero.
     *
     * @param accountId the account ID to validate.
     * @throws InvalidPurchaseException if the account ID is null or less than or equal to zero.
     */
    private void validateAccountId(Long accountId) {
        if (accountId == null || accountId <= 0) {
            String message = "Account ID must be greater than zero, got: " + accountId;
            LOG.warn(message);
            throw new InvalidPurchaseException(message);
        }
    }

    /**
     * Validates the request array is non-null, non-empty, and contains no null or zero-quantity entries.
     *
     * @param ticketTypeRequests the array of ticket requests to validate.
     * @throws InvalidPurchaseException if the array is null, empty, or contains invalid entries.
     */
    private void validateRequestsArray(TicketTypeRequest[] ticketTypeRequests) {
        if (ticketTypeRequests == null) {
            String message = "Ticket requests must not be null";
            LOG.warn(message);
            throw new InvalidPurchaseException(message);
        }
        if (ticketTypeRequests.length == 0) {
            String message = "At least one ticket request must be provided";
            LOG.warn(message);
            throw new InvalidPurchaseException(message);
        }
        for (TicketTypeRequest request : ticketTypeRequests) {
            if (request == null) {
                String message = "Individual ticket requests must not be null";
                LOG.warn(message);
                throw new InvalidPurchaseException(message);
            }
            if (request.getNoOfTickets() <= 0) {
                String message = "Ticket quantity must be greater than zero, got: " + request.getNoOfTickets();
                LOG.warn(message);
                throw new InvalidPurchaseException(message);
            }
        }
    }

    /**
     * Validates the adult, child, and infant counts against the cinema business rules.
     *
     * @param ticketTypeRequests the pre-validated array of ticket requests.
     * @throws InvalidPurchaseException if count-based rules are violated.
     */
    private void validateTicketCounts(TicketTypeRequest[] ticketTypeRequests) {
        int adultCount = 0;
        int childCount = 0;
        int infantCount = 0;

        for (TicketTypeRequest request : ticketTypeRequests) {
            switch (request.getTicketType()) {
                case ADULT  -> adultCount  += request.getNoOfTickets();
                case CHILD  -> childCount  += request.getNoOfTickets();
                case INFANT -> infantCount += request.getNoOfTickets();
            }
        }

        int totalTickets = adultCount + childCount + infantCount;
        if (totalTickets > MAX_TICKETS) {
            String message = "Total ticket count exceeds maximum of " + MAX_TICKETS + ", got: " + totalTickets;
            LOG.warn(message);
            throw new InvalidPurchaseException(message);
        }

        if (childCount > 0 && adultCount == 0) {
            String message = "Child tickets cannot be purchased without at least one Adult ticket";
            LOG.warn(message);
            throw new InvalidPurchaseException(message);
        }

        if (infantCount > 0 && adultCount == 0) {
            String message = "Infant tickets cannot be purchased without at least one Adult ticket";
            LOG.warn(message);
            throw new InvalidPurchaseException(message);
        }

        if (infantCount > adultCount) {
            String message = "Number of infants (" + infantCount + ") cannot exceed number of adults (" + adultCount + ")";
            LOG.warn(message);
            throw new InvalidPurchaseException(message);
        }
    }
}