# Decision Log - Coding Assessment

**Build:** `mvn clean verify` → 32 tests, 0 failures, JaCoCo ≥ 90% coverage check passed

> **Relationship to Approach.md**
> `Approach.md` is the pre-implementation planning document - it records design intent, validation rules, test strategy, and design principles decided _before_ writing code.
> This document is the post-implementation record - it captures decisions that only emerged _during_ coding, problems encountered and how they were resolved, low-level implementation choices not visible in a planning document, and how the final result diverged from or refined the original plan.
> The two documents are intentionally non-overlapping. Where a decision was already covered in `Approach.md`, this document references that section rather than repeating it.

---

## SECTION 1: CONTEXT AND OBJECTIVE

### What the task is

Implement `TicketServiceImpl` in a pre-scaffolded Maven project for a cinema ticket booking system (template: `https://github.com/dwp/cinema-tickets`). The interface, domain model, third-party stubs, and exception class were all provided.

### What the final output achieves

A production-quality implementation of `TicketService.purchaseTickets` that validates, calculates, and delegates to the payment and reservation services in the correct order - with structured logging, a JaCoCo ≥ 90% coverage gate, and AssertJ-based tests that verify exception messages, not just types.

### Key constraints

- Java 21 or above. No DI framework.
- `TicketTypeRequest`, `TicketPaymentService`, and `SeatReservationService` must not be modified.
- `InvalidPurchaseException` is the only permitted exception for rule violations.
- `purchaseTickets` is the only public method on `TicketServiceImpl`.
- All validation must complete before any external service call.

_For the full list of business rules and design principles (SRP, fail-fast, constructor injection, thread safety, named constants, switch expressions, exception strategy) see `Approach.md` Section3, Section5._

---

## SECTION 2: HIGH-LEVEL DESIGN DECISIONS

High-level design decisions - including the `TicketPurchaseValidator` extraction, constructor injection, fail-fast validation ordering, and thread safety - were all made and documented before implementation began. See `Approach.md` Section5 for the full rationale on each.

This section covers only decisions that were not anticipated in the plan and arose during implementation.

---

### Decision 1 - Add four production-readiness upgrades

**What was decided:** JaCoCo coverage gate (≥ 90% instruction coverage), AssertJ assertion library with exception message verification, SLF4J + Logback structured logging, and `@DisplayName` on all test methods.

**Why:** The plan (`Approach.md` Section9 "What I Would Do Next") listed logging and configuration as production concerns but left them as future work. During implementation, the effort to add all four upgrades was low relative to the signal they send to an assessor evaluating a senior submission:

- **JaCoCo:** No CI pipeline ships without a coverage gate. Running `mvn verify` and seeing `All coverage checks have been met` is a stronger signal than just passing tests.
- **AssertJ + message verification:** `assertThrows` only proves a type was thrown. In a class with nine validation rules, it cannot distinguish which rule fired. `hasMessageContaining(...)` closes that gap.
- **SLF4J/Logback:** A service that processes payments and reservations with no log output is undiagnosable in production.
- **`@DisplayName`:** Test method names are read by humans in CI reports. Plain English descriptions communicate intent; method names alone are ambiguous.

**Alternatives considered:**

- Leaving the project at minimum viable - rejected because it would not distinguish a senior submission from a junior one.
- Spring Boot or another framework - rejected; the brief prohibits DI frameworks and the overhead is not proportionate.

**Tradeoffs:** More dependencies and pom.xml configuration. The benefit is demonstrable production-awareness at review time.

---

### Decision 2 - Target Java 21 bytecode despite running on Java 26 JVM

**What was decided:** `maven.compiler.source` and `maven.compiler.target` are set to `21`, not the developer's installed JDK version (26).

**Why this was forced:** Initially, the compiler target was set to `26` (the developer's JDK). This caused JaCoCo to fail fatally:

```
Unsupported class file major version 70
```

JaCoCo 0.8.12 - the latest release - supports class file versions up to 67 (Java 23). Java 26 produces major version 70.

Downgrading to target `21` (major version 65) resolves the issue. The assessment requirement is "Java 21 or above". Compiling to Java 21 bytecode running on a Java 26 JVM fully satisfies that requirement. This is standard practice and is the LTS version named explicitly in the brief.

**Alternatives considered:**

- Target Java 26, remove JaCoCo - rejected; removing JaCoCo removes one of the key upgrades.
- Target Java 26, use a JaCoCo snapshot - rejected; snapshots are not appropriate in a submission codebase.
- Target Java 24 or 25 - rejected; same JaCoCo incompatibility (major versions 68 and 69 respectively).
- Target Java 23 - considered; rejected in favour of 21 because 21 is the LTS version named in the brief and is more meaningful.

**Fix required:** `mvn clean verify` (not just `mvn verify`) was needed to flush cached Java 26 class files from a prior `mvn compile` run before the target version was changed.

---

## SECTION 3: LOW-LEVEL TECHNICAL / CODING DECISIONS

_Decisions already covered in `Approach.md` - including validation ordering, the 9 rules and their conditions, named constants, switch expressions, exception message design, and immutability of `TicketTypeRequest` - are not repeated here._

---

### Validation: two-pass vs one-pass array iteration

`TicketPurchaseValidator` iterates the request array twice: once in `validateRequestsArray` (structural checks) and once in `validateTicketCounts` (count-based business rules).

**Why two passes:** By the time `validateTicketCounts` runs, every element is guaranteed non-null with a positive quantity. This avoids mixing structural guards (`request == null`, `getNoOfTickets() <= 0`) inside the accumulator loop. A combined single-pass version is marginally more efficient but conflates two different levels of validation into one method body.

**Alternative:** A single loop that accumulates counts while also null-checking each element - rejected for readability. The validator already has three private methods to separate concerns; a combined loop would collapse two of them.

---

### Data handling: `TicketTypeRequest[]` array, not a collection

The interface declares `TicketTypeRequest... ticketTypeRequests`, which Java resolves to an array at the call site. The array is passed directly to the validator rather than converting it to a `List` or `Map`.

**Why:** A `Map<Type, Integer>` grouping by ticket type was considered but rejected:

- It adds a `stream().collect(groupingBy(...))` allocation on every call.
- The accumulator loop (`adultCount += ...`) is more readable than `counts.getOrDefault(ADULT, 0)` repeated three times.
- Sequential read access is the only operation needed; an array is sufficient.

---

### Function boundaries: logic kept inline in `purchaseTickets`

The calculation logic (two counters, two arithmetic operations) is written inline rather than extracted into private helper methods.

**Why:** The logic is six lines. Private helpers (`calculateTotalAmount`, `calculateSeatsToReserve`) would add indirection without adding clarity. The threshold for extraction is when the logic becomes complex enough that its purpose is not immediately apparent inline - that threshold is not met here.

---

### Naming: local variable names

| Variable                                  | Alternatives considered                | Decision                                                                                                                   |
| ----------------------------------------- | -------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| `adultCount`, `childCount`, `infantCount` | `adults`, `numAdults`, `adultTickets`  | `*Count` suffix makes it unambiguous that these are quantities, not lists                                                  |
| `totalAmount`                             | `total`, `totalCost`, `paymentAmount`  | `totalAmount` avoids the ambiguous `total` (total what?) and matches the semantic of `makePayment(accountId, totalAmount)` |
| `seatsToReserve`                          | `seatCount`, `totalSeats`, `seats`     | `seatsToReserve` mirrors the method name `reserveSeat(...)` it feeds                                                       |
| `MAX_TICKETS`                             | `MAXIMUM_TICKET_COUNT`, `TICKET_LIMIT` | Concise and self-explanatory within the domain context                                                                     |
| `LOG`                                     | `logger`, `LOGGER`                     | `LOG` is uppercase as required for `static final` fields; shorter than `LOGGER` with the same clarity                      |

---

### Testing: Mockito `mock-maker-subclass` extension file

**File created:** `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker`  
**Content:** `mock-maker-subclass`

**Why it was needed:** Mockito's default mock maker uses byte-buddy agent instrumentation, which requires attaching to the running JVM. On Java 25 and above, this attachment was restricted, causing:

```
org.mockito.exceptions.base.MockitoException: Could not self-attach to current VM using external process
```

This fired when `TicketPurchaseValidator` (a concrete class) was annotated with `@Mock` in `TicketServiceImplTest`.

**Why subclass mock maker:** It generates a runtime subclass using standard class loading - no agent attachment, no JVM instrumentation, compatible with all JVM versions. The alternative (making `TicketPurchaseValidator` implement an interface purely for mockability) would change the public API without a real-world reason.

---

### Testing: `PotentialStubbingProblem` in validation delegation test

**Original code (broken):**

```java
doThrow(new InvalidPurchaseException("invalid"))
    .when(validator).validate(0L, new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 1)});

assertThatThrownBy(() -> ticketService.purchaseTickets(0L, new TicketTypeRequest(ADULT, 1)))
    .isInstanceOf(InvalidPurchaseException.class);
```

**Problem:** Mockito strict stubbing raised `PotentialStubbingProblem`. The stubbed array `new TicketTypeRequest[]{new TicketTypeRequest(ADULT, 1)}` and the actual array produced by the varargs call are two different object instances. They do not match by reference, so Mockito detected a stub that never matched any invocation.

**Fix applied:**

```java
doThrow(new InvalidPurchaseException("invalid"))
    .when(validator).validate(anyLong(), any(TicketTypeRequest[].class));
```

`any(TicketTypeRequest[].class)` matches by type rather than reference. This correctly stubs the call regardless of which array instance is produced.

---

### Testing: AssertJ patterns used

| Scenario                             | Pattern used                                                          | Why                                                                           |
| ------------------------------------ | --------------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| Exception expected with message      | `assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)` | Verifies both that the right type was thrown and that the correct rule fired  |
| No exception expected (happy path)   | `assertThatNoException().isThrownBy(...)`                             | Explicitly names the expectation rather than relying on the test not throwing |
| External services must not be called | `verifyNoInteractions(paymentService, reservationService)`            | Asserts the fail-fast guarantee at the mock level                             |

---

### Security considerations

No injection surface exists: no SQL, no file I/O, no network calls in the implementation. The only external calls are to the provided stub interfaces.

Log entries include `accountId`, `totalAmount`, and `seatsToReserve`. No personal data, card numbers, or ticket holder names exist in the domain model.

Account ID validation (`accountId == null || accountId <= 0`) prevents invalid IDs from being forwarded to a payment processor - defence-in-depth in addition to being a business rule.

---

### Performance considerations

No special optimisations were made. Inputs are small (at most 25 tickets, at most 3 types, array iterated at most twice). The two-pass vs one-pass choice was made on readability grounds; the performance difference is immeasurable in practice.

---

## SECTION 4: ITERATION AND REFINEMENT

### Step 1 - Core implementation

`TicketServiceImpl` and `TicketPurchaseValidator` were implemented against the plan in `Approach.md`. `InvalidPurchaseException` was extended with a message constructor (the template provided only a no-arg constructor).

---

### Step 2 - Java version target

Compiler source/target was initially set to `26` (the developer's installed JDK). This compiled successfully but caused JaCoCo to fail with `Unsupported class file major version 70`. Reverted to `21`. `mvn clean verify` (not `mvn verify`) was required to flush cached bytecode from the prior compile run.

---

### Step 3 - Mockito compatibility

On Java 26, the inline mock maker failed with `Could not self-attach to current VM`. Fixed by creating `src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` with content `mock-maker-subclass`. This switches the full test suite to the subclass mock strategy, which has no JVM agent dependency.

---

### Step 4 - `PotentialStubbingProblem` in validation test

The validation delegation test had a reference-mismatch between the stubbed array and the runtime-produced varargs array. Fixed by replacing the concrete array matcher with `any(TicketTypeRequest[].class)`. See Section 3 for the full explanation.

---

### Step 5 - Comment style

Three rounds of feedback shaped the Javadoc style:

1. `TicketServiceImplTest.java` had no comments at all. Fixed: added full Javadoc to all test methods.
2. The `purchaseTickets` Javadoc was "doing too much" - it described calculation logic inline. Fixed: trimmed every method to a one-sentence summary plus `@param`/`@throws` only.
3. The `TicketPurchaseValidator` class-level Javadoc listed all nine rules as bullets. Fixed: reduced to a single sentence - "Validates ticket purchase requests against the cinema ticketing business rules."

**Principle established:** Javadoc describes _intent_ (the semantic contract), not _mechanics_ (what the code does line by line). Detailed rule enumeration belongs in tests and in `Approach.md` Section3, not in class-level Javadoc.

---

### Step 6 - Standout upgrades

All four upgrades (JaCoCo, AssertJ, SLF4J/Logback, `@DisplayName`) were added after the core implementation and tests were verified passing. Final `mvn clean verify` result:

```
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
All coverage checks have been met.
BUILD SUCCESS
```

---

## SECTION 5: FINAL JUSTIFICATION

### Why the final version is the best choice

**Correctness:** All 9 validation rules are implemented, covered by 17 dedicated tests, and verified against explicit exception messages - not just exception types.

**Isolation:** `TicketPurchaseValidator` is independently testable with no mocks. `TicketServiceImpl` tests use a mocked validator so the two concerns never bleed into each other's test suites.

**Production-readiness:** JaCoCo ≥ 90% coverage gate, structured logging, readable test output, and defensive input validation with descriptive error messages.

**Simplicity:** No frameworks, no unnecessary abstractions, no speculative generality. The implementation is approximately 30 lines of business logic. Every line earns its place.

**Compatibility:** Java 21 target bytecode runs on any JVM 21+, satisfies the assessment requirement, and stays within JaCoCo's supported class file version range.

---

### Compromises made

| Compromise                                            | Reason                                                                                                                                        |
| ----------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- |
| Bytecode targets Java 21, not 26                      | JaCoCo 0.8.12 does not support Java 26 bytecode (class file major version 70). Java 21 is the LTS version named in the assessment brief.      |
| `mock-maker-subclass` instead of interface-based mock | Avoids changing `TicketPurchaseValidator`'s public API purely for testability. Works correctly on Java 25+.                                   |
| Validator iterates the array twice                    | Two clean passes (structure, then counts) are preferred over one combined pass with interleaved concerns, despite the trivial added overhead. |

---

### Known limitations

**JaCoCo ceiling at Java 23:** If the project is migrated to target Java 24+ bytecode in future, the JaCoCo `check` goal will fail with an unsupported class file version error until a compatible JaCoCo release is published.

**No `logback.xml`:** Logback auto-configures with a default console appender, which is sufficient for a coding assessment. A production service would have an explicit `logback.xml` for structured JSON output and log level configuration per environment.

**No integration test:** The project contains only unit tests. An integration test wiring all three real implementations together would catch any contract mismatches between the service and the actual third-party stubs. The brief does not require this, and the provided `*Impl` stubs define no verifiable contract beyond their method signatures.

**`TicketPurchaseValidator` is instantiated per service instance:** Because it is stateless, it could safely be a singleton. In a real application a DI container would manage this. In the assessment context it is not meaningful, but worth noting as a discussion point in a team review.
