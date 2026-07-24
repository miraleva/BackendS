# SANNY Backend

AI-powered travel assistant backend for hotel and flight search, comparison, and reservation using the TourVisio API.

**Repositories**

- **Backend:** Current repository
- **Frontend:** https://github.com/miraleva/FrontendS

---

## Overview

SANNY is a conversational travel assistant that understands natural language queries such as:

> "I'm looking for a hotel in Antalya for two adults between July 20–25."

It extracts the user's intent and travel criteria, searches the TourVisio API in real time, and guides the user through the reservation process.

The system currently supports:

- Hotel search
- Flight search
- Reservation flow
- Multi-language conversations (Turkish, English, German, Russian)
- AI-powered conversational interface

---

## Technology Stack

- Java 21
- Spring Boot 3.3
- PostgreSQL
- Docker
- Google Gemini API
- OpenRouter API
- Llama
- TourVisio API
- JWT Authentication
- BCrypt Password Hashing
- JUnit 5
- Mockito

---

## Architecture

### AI Agent Architecture

The system separates AI responsibilities into two independent agents.

| Component | Responsibility |
|-----------|----------------|
| **ExtractionAgent** | Extracts user intent and search criteria (destination, dates, passengers, etc.) into structured JSON. |
| **ResponseAgent** | Generates all user-facing natural language responses, including greetings, missing information prompts, search summaries, and error messages. |

---

### AI Fallback Strategy

To ensure high availability, the backend uses a multi-layer AI fallback chain.

```
Google Gemini
      ↓
OpenRouter
      ↓
Llama
      ↓
Rule-based Regex Fallback
```

If one provider becomes unavailable or exceeds its quota, the next provider automatically takes over.

As a final safeguard, a rule-based extraction mechanism guarantees that essential functionality remains available even if all external AI providers fail.

---

### Conversation State Machine

```
GATHERING
    ↓
AWAITING_CONFIRM
    ↓
BOOKING
```

- **GATHERING** – Collect travel criteria.
- **AWAITING_CONFIRM** – Present search results and wait for user selection.
- **BOOKING** – Continue with the reservation workflow.

---

## Security

The backend follows several security principles:

- Internal prompts are never exposed.
- API keys remain confidential.
- Reservation confirmation is always required.
- Payment or credit card information is never requested.
- Hotel, flight, and pricing data are never fabricated.
- Prompt injection attempts are filtered before reaching AI models.

---

## Project Structure

```
src/main/java/com/santsg/tourvisio/

├── agent/
│   ├── ExtractionAgent
│   ├── ResponseAgent
│   └── PromptConstants
│
├── chat/
│   ├── SearchCriteriaExtractor
│   ├── SearchCriteriaValidator
│   └── CriteriaMissingFieldsService
│
├── client/
│   ├── GeminiClient
│   ├── TourVisioHotelApiClient
│   └── TourVisioFlightApiClient
│
├── controller/
│   ├── AuthController
│   ├── ChatController
│   ├── ReservationController
│   └── ProfileController
│
├── dto/
├── entity/
├── exception/
└── service/
```

---

## Getting Started

### Prerequisites

- Java 21+
- Maven
- Docker
- PostgreSQL
- Google Gemini API Key
- OpenRouter API Key

---

### Start PostgreSQL

```bash
docker run \
  --name tourvisio-postgres \
  -e POSTGRES_PASSWORD=<password> \
  -e POSTGRES_DB=tourvisio \
  -p 5432:5432 \
  -d postgres:16
```

---


### Run the Application

```powershell
.\start-local.ps1
```

Backend URL:

```
http://localhost:8083
```

Swagger UI:

```
http://localhost:8083/swagger-ui.html
```

---

## Running Tests

```bash
.\mvnw clean test
```

---

## Localization

Responses are generated dynamically based on the user's language.

Supported languages:

- Turkish
- English
- German
- Russian

Localization resources are stored under:

```
src/main/resources/messages_*.properties
```

---

## REST API

| Method | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/auth/signup` | Register a new user |
| POST | `/api/auth/login` | Authenticate and generate JWT |
| POST | `/api/chat/message` | Main conversational AI endpoint |
| GET | `/api/chat/sessions` | Retrieve chat history |
| POST | `/api/hotels/search` | Search hotels |
| POST | `/api/flights/search` | Search flights |
| GET | `/api/reservations` | List reservations |
| POST | `/api/reservations` | Create a reservation |

---

## Frontend

The React frontend for this project is available at:

https://github.com/miraleva/FrontendS
