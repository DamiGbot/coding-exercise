# Coding Assessment

An implementation of a cinema ticket booking service in Java. The service validates purchase requests, calculates the correct payment amount and seat count, and delegates to third-party payment and reservation services.

---

## Prerequisites

- JDK 21 or above
- Maven 3.8 or above

Verify your setup:

```bash
java -version
mvn -version
```

---

## Build

```bash
mvn compile
```

---

## Run tests

```bash
mvn test
```

Runs all 32 unit tests across two test classes:

- `TicketServiceImplTest` - verifies payment amounts, seat counts, and delegation behaviour
- `TicketPurchaseValidatorTest` - verifies all 9 validation rules in isolation

---

## Run full verification (tests + coverage check)

```bash
mvn clean verify
```

This compiles, runs all tests, and enforces a JaCoCo instruction coverage gate of 90% or above on the `uk.gov.dwp` packages. Expected output:

```
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.
BUILD SUCCESS
```

The full coverage report is generated at:

```
target/site/jacoco/index.html
```

---

## Project structure

```
src/
├── main/java/
│   ├── thirdparty/                          # Provided third-party stubs (do not modify)
│   │   ├── paymentgateway/
│   │   │   ├── TicketPaymentService.java    # Payment service interface
│   │   │   └── TicketPaymentServiceImpl.java
│   │   └── seatbooking/
│   │       ├── SeatReservationService.java  # Seat reservation interface
│   │       └── SeatReservationServiceImpl.java
│   └── uk/gov/dwp/uc/pairtest/
│       ├── domain/
│       │   └── TicketTypeRequest.java       # Immutable ticket request value object (do not modify)
│       ├── exception/
│       │   └── InvalidPurchaseException.java
│       ├── validation/
│       │   └── TicketPurchaseValidator.java # All business rule validation
│       ├── TicketService.java               # Interface (do not modify)
│       └── TicketServiceImpl.java           # Main service implementation
│
└── test/java/uk/gov/dwp/uc/pairtest/
    ├── TicketServiceImplTest.java
    └── validation/
        └── TicketPurchaseValidatorTest.java
```

---

## Business rules

| Rule                                  | Detail                                                                |
| ------------------------------------- | --------------------------------------------------------------------- |
| Maximum 25 tickets per purchase       | Total across all ticket types                                         |
| Adult tickets: £25 each               | Charged and allocated a seat                                          |
| Child tickets: £15 each               | Charged and allocated a seat; require at least one Adult              |
| Infant tickets: £0                    | No charge, no seat; sit on an Adult's lap; require at least one Adult |
| Infants cannot outnumber Adults       | One infant per adult lap                                              |
| Account ID must be greater than zero  | Null or non-positive IDs are rejected                                 |
| At least one ticket must be requested | Empty or null request arrays are rejected                             |

---

## Further reading

| Document                         | Purpose                                                                                                                                                           |
| -------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| [Approach.md](Approach.md)       | Pre-implementation planning: design decisions, validation rules, test strategy, assumptions, and what would be done next in a real team setting                   |
| [DecisionLog.md](DecisionLog.md) | Post-implementation record: problems encountered during coding, how they were resolved, and low-level implementation choices not visible in the planning document |
