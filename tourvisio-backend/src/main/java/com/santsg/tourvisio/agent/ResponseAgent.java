package com.santsg.tourvisio.agent;

import com.santsg.tourvisio.chat.SearchCriteria;
import com.santsg.tourvisio.client.AIFallbackChain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class ResponseAgent {

    private static final Logger log = LoggerFactory.getLogger(ResponseAgent.class);

    /** Gemini → OpenRouter (ücretsiz) fallback zinciri. Bkz. {@code AIProviderConfig}. */
    private final AIFallbackChain geminiClient;
    private final MessageSource messageSource;

    public ResponseAgent(@Qualifier("responseAiChain") AIFallbackChain geminiClient, MessageSource messageSource) {
        this.geminiClient = geminiClient;
        this.messageSource = messageSource;
    }

    private static final String CATEGORY_TERMINOLOGY_CONSTRAINTS =
            "\nCATEGORY & TERMINOLOGY STRICT CONSTRAINTS:\n" +
            "- You MUST follow strict travel category terminology based on the active search type:\n" +
            "  * FOR HOTEL SEARCH (HOTEL_SEARCH / Category = HOTEL):\n" +
            "    - NEVER use flight/trip terms such as 'Gidiş', 'Dönüş', 'Yola çıkmak', 'Yola çıkmayı planlıyorsunuz', 'Uçuş', 'Uçak', 'Bilet', 'Yolcu', 'Flight', 'Departure', 'Return'.\n" +
            "    - ONLY use hotel terms: 'Giriş Tarihi (Check-in)', 'Çıkış Tarihi (Check-out)', 'Konaklama', 'Misafir Sayısı', 'Otel', 'Oda'.\n" +
            "  * FOR FLIGHT SEARCH (FLIGHT_SEARCH / Category = FLIGHT):\n" +
            "    - NEVER use hotel terms such as 'Giriş tarihi', 'Çıkış tarihi', 'Otel', 'Konaklama', 'Misafir', 'Oda'.\n" +
            "    - ONLY use flight terms: 'Gidiş Tarihi', 'Dönüş Tarihi', 'Yolcu Sayısı', 'Uçuş', 'Havalimanı', 'Kalkış', 'Varış'.\n";

    // ─────────────────────────────────────────────────────────────────────────
    // Public Scenarios
    // ─────────────────────────────────────────────────────────────────────────

    public String decline(SearchCriteria criteria, boolean isTerminated) {
        return decline(criteria, isTerminated, null);
    }

    public String decline(SearchCriteria criteria, boolean isTerminated, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);

        String prompt = String.format(
                "You are a warm, polite, and professional travel assistant.\n" +
                "The user made a request outside of your supported services (e.g., bus tickets, train tickets, weather, car rental, visa, etc.).\n" +
                "Write a hospitable, natural response explaining that this assistant currently supports hotel reservations and flight bookings.\n" +
                "Invite them to ask for help with hotel or flight searches if needed.\n" +
                "Write the response in %s — matching the user's language.%s\n" +
                "Keep a helpful, friendly tone, max 1-2 emojis. Context status: %s.\n" +
                "Return ONLY the response text itself, no extra notes.",
                targetLanguage, userMessageClause(userMessage), isTerminated ? "TERMINATED" : "ACTIVE"
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] Decline AI generation failed, using fallback localization: {}", e.getMessage());
        }

        String key = isTerminated ? "out.of.scope.terminated" : "out.of.scope";
        return messageSource.getMessage(key, null, locale);
    }

    public String profanityTerminated(SearchCriteria criteria, String userMessage) {
        Locale locale = criteria != null ? resolveLocale(criteria) : detectFallbackLocale(userMessage);
        String targetLanguage = criteria != null ? resolveLanguageName(criteria) : "Turkish";

        String prompt = String.format(
                "The user sent a message containing profanity, insults, or abusive language: \"%s\".\n" +
                "Write a firm, polite, and professional message terminating the conversation due to inappropriate language.\n" +
                "Inform the user that the conversation is ended, and that if they need assistance in the future, they can start a new conversation.\n" +
                "Write the response in %s.\n" +
                "Return ONLY the response text itself, no extra notes.",
                userMessage != null ? userMessage.replace("\"", "\\\"") : "", targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] Profanity termination AI generation failed, using fallback: {}", e.getMessage());
        }

        return messageSource.getMessage("error.profanity.terminated", null, locale);
    }

    public String irrelevantWarning(int warningLevel, SearchCriteria criteria, String userMessage) {
        Locale locale = criteria != null ? resolveLocale(criteria) : detectFallbackLocale(userMessage);
        String targetLanguage = criteria != null ? resolveLanguageName(criteria) : "Turkish";

        String levelInstruction;
        if (warningLevel == 1) {
            levelInstruction = "Level 1 warning: Politely indicate that the message could not be understood. Enthusiastically invite the user to share their hotel or flight travel plans (e.g. destination, travel dates, or guest count). Vary wording naturally.";
        } else if (warningLevel == 2) {
            levelInstruction = "Level 2 warning: Politely explain that you still cannot understand their request. Mention that the conversation will be ended soon if no booking-related request is provided.";
        } else {
            levelInstruction = "Level 3 final warning: Politely terminate the conversation because no valid request was received. Invite them to start a new chat if they need help in the future.";
        }

        String prompt = String.format(
                "The user sent an unreadable, random, or gibberish message: \"%s\".\n" +
                "%s\n" +
                "Write the response in %s matching the user's language.\n" +
                "Tone: Professional and helpful, max 1-2 emojis. Avoid repeating identical robotic sentences.\n" +
                "Return ONLY the response text itself.",
                userMessage != null ? userMessage.replace("\"", "\\\"") : "", levelInstruction, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] Irrelevant warning Level {} AI generation failed, using fallback: {}", warningLevel, e.getMessage());
        }

        String key = (warningLevel == 1) ? "irrelevant.warning.1"
                : (warningLevel == 2) ? "irrelevant.warning.2"
                : "irrelevant.warning.3";
        return messageSource.getMessage(key, null, locale);
    }

    private Locale detectFallbackLocale(String message) {
        if (message == null || message.trim().isEmpty()) {
            return Locale.ENGLISH;
        }
        String lower = message.trim().toLowerCase(Locale.forLanguageTag("tr-TR"));
        
        // Turkish
        if (lower.contains("merhaba") || lower.contains("selam") || lower.contains("nasılsın")
                || lower.contains("ç") || lower.contains("ş") || lower.contains("ğ") || lower.contains("ı")
                || lower.contains("ü") || lower.contains("ö")) { // Tr also has ü and ö but usually handled together, wait, let's keep it simple
            return Locale.forLanguageTag("tr-TR");
        }
        // German
        if (lower.contains("hallo") || lower.contains("guten") || lower.contains("morgen")
                || lower.contains("ß") || lower.contains("ä")) {
            return Locale.GERMAN;
        }
        // Russian
        if (lower.contains("привет") || lower.contains("здравствуйте") || lower.matches(".*[а-яА-Я].*")) {
            return Locale.forLanguageTag("ru-RU");
        }
        
        return Locale.ENGLISH;
    }

    public String welcome(String userMessage) {
        Locale locale = detectFallbackLocale(userMessage);
        // Hedef dili net bir isimle (ör. "English") sabitliyoruz — sadece "write in the
        // language of this message" demek, mesaj emoji/anlamsız metin gibi hiçbir dil
        // sinyali içermediğinde modelin rastgele bir dile (ör. İspanyolca) savrulmasına
        // yol açıyordu. Diğer tüm ResponseAgent metodları zaten somut bir "Target: X"
        // dili veriyor; welcome() de aynı şekilde davranmalı.
        String targetLanguage = "tr".equals(locale.getLanguage()) ? "Turkish"
                : "de".equals(locale.getLanguage()) ? "German"
                : "ru".equals(locale.getLanguage()) ? "Russian"
                : "English";
        String prompt = String.format(
                "The user just started a chat and sent their first message: \"%s\".\n" +
                "You are an enthusiastic, warm, and professional travel consultant.\n" +
                "Write a warm, welcoming onboarding message as a travel consultant.\n" +
                "Start with a natural, pleasant greeting (e.g. 'Harika bir seyahat planlamaya hazır mısınız? 😊' / 'Ready to plan a wonderful trip? 😊').\n" +
                "Briefly explain that you need their destination, dates, and guest count to find the best hotel or flight options.\n" +
                "Write the response in the same language as the user's message above. If the message has no " +
                "identifiable language (e.g. only emoji, random characters, numbers, or gibberish), default to %s — " +
                "do NOT guess an unrelated language.\n" +
                "Tone & style: Hospitable, professional consultant tone. Max 2-3 emojis. Avoid robotic phrases like 'Bilgileriniz alınmıştır' or 'Arama gerçekleştiriliyor'.\n" +
                "Return ONLY the response itself, no extra notes or greetings.",
                userMessage != null ? userMessage.replace("\"", "\\\"") : "",
                targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] welcome AI generation failed", e);
        }

        return messageSource.getMessage("welcome.intent", null, locale);
    }

    public String clarify(SearchCriteria criteria) {
        return clarify(criteria, null);
    }

    public String clarify(SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);

        String prompt = String.format(
                "You are a warm and professional travel consultant.\n" +
                "Ask the user in a friendly conversation whether they would like to search for a hotel or a flight ticket.\n" +
                "Vary your opening naturally (e.g. 'Harika! Size yardımcı olmaktan memnuniyet duyarım 😊' / 'Great! I would love to help with your trip 😊').\n" +
                "Write the response in %s — matching the language the user is writing in.%s\n" +
                "Max 1-2 emojis. Return ONLY the question itself, no extra notes.",
                targetLanguage, userMessageClause(userMessage)
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] Clarify AI generation failed, using fallback localization: {}", e.getMessage());
        }

        return messageSource.getMessage("clarify.intent", null, locale);
    }

    public String askMissing(List<String> missingFields, SearchCriteria criteria) {
        return askMissing(missingFields, criteria, null);
    }

    public String askMissing(List<String> missingFields, SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);

        String poiInstruction = "";
        if (userMessage != null && (missingFields.contains("locationOrHotelName") || missingFields.contains("konum veya otel adı") || missingFields.contains("varış noktası") || missingFields.contains("arrivalLocation"))) {
            String lowerMsg = userMessage.toLowerCase(Locale.forLanguageTag("tr-TR"));
            String foundPoi = null;
            if (lowerMsg.contains("lunapark")) foundPoi = "lunapark";
            else if (lowerMsg.contains("müze") || lowerMsg.contains("muze")) foundPoi = "müze";
            else if (lowerMsg.contains("plaj")) foundPoi = "plaj";
            else if (lowerMsg.contains("havalimanı") || lowerMsg.contains("havalimani") || lowerMsg.contains("havaalanı") || lowerMsg.contains("havaalani")) foundPoi = "havalimanı";
            else if (lowerMsg.contains("otogar")) foundPoi = "otogar";
            else if (lowerMsg.contains("merkez")) foundPoi = "merkez";
            else if (lowerMsg.contains("beach")) foundPoi = "beach";
            else if (lowerMsg.contains("museum")) foundPoi = "museum";
            else if (lowerMsg.contains("airport")) foundPoi = "airport";

            if (foundPoi != null) {
                // Adapt grammatical suffix for Turkish
                String suffix = "yakınında";
                String targetPoi = foundPoi;
                if ("lunapark".equals(foundPoi)) { suffix = "lunaparka"; }
                else if ("müze".equals(foundPoi)) { suffix = "müzeye"; }
                else if ("plaj".equals(foundPoi)) { suffix = "plaja"; }
                else if ("havalimanı".equals(foundPoi)) { suffix = "havalimanına"; targetPoi = "havalimanının"; }
                else if ("otogar".equals(foundPoi)) { suffix = "otogara"; targetPoi = "otogarın"; }
                else if ("merkez".equals(foundPoi)) { suffix = "merkeze"; targetPoi = "merkezin"; }
                else if ("beach".equals(foundPoi)) { suffix = "the beach"; targetPoi = "beach's"; }
                else if ("museum".equals(foundPoi)) { suffix = "the museum"; targetPoi = "museum's"; }
                else if ("airport".equals(foundPoi)) { suffix = "the airport"; targetPoi = "airport's"; }

                poiInstruction = String.format(
                    "\nCRITICAL SPECIAL RULE: The user mentioned a general point of interest '%s' but did not specify a city/location. " +
                    "Instead of a generic destination question, ask them politely which city's '%s' they are referring to. " +
                    "Format of response MUST be similar to: '%s yakın bir otel bulmaktan memnuniyet duyarım! Hangi şehirdeki %s yakınında konaklamak istersiniz? (Örn: Antalya, İstanbul)'. " +
                    "Match the tone and language of the user message.",
                    foundPoi, foundPoi, capitalize(suffix), targetPoi
                );
            }
        }

        String knownDetailsInstruction = "";
        String dateStateInstruction = "";

        if (criteria != null) {
            boolean isFlight = "FLIGHT_SEARCH".equals(criteria.getSearchType());
            java.time.LocalDate startDate = isFlight ? criteria.getDepartureDate() : criteria.getCheckInDate();
            java.time.LocalDate endDate = isFlight ? criteria.getReturnDate() : criteria.getCheckOutDate();

            if (startDate != null && endDate == null) {
                if (isFlight) {
                    dateStateInstruction = String.format(
                        "\nSTRICT DATE ACKNOWLEDGMENT RULE (FLIGHT):\n" +
                        "- Departure Date (Gidiş Tarihi) is ALREADY KNOWN: '%s'.\n" +
                        "- Return Date (Dönüş Tarihi) is MISSING / UNCLEAR.\n" +
                        "- You MUST explicitly acknowledge the Departure Date ('Gidiş tarihinizi (%s) not aldım ✈️').\n" +
                        "- Then ask ONLY whether they want a return flight or a one-way ticket (e.g. 'Sadece gidiş mi planlıyorsunuz, yoksa dönüş uçuşuna da bakayım mı?' / 'Is this a one-way trip, or should I look for a return flight too?').\n" +
                        "- NEVER call '%s' a return date or ask for departure date again!",
                        formatDisplayDate(startDate), formatDisplayDate(startDate), formatDisplayDate(startDate)
                    );
                } else {
                    dateStateInstruction = String.format(
                        "\nSTRICT DATE ACKNOWLEDGMENT RULE (HOTEL):\n" +
                        "- Check-in Date (Giriş Tarihi) is ALREADY KNOWN: '%s'.\n" +
                        "- Check-out Date (Çıkış Tarihi) is MISSING.\n" +
                        "- You MUST explicitly acknowledge the Check-in Date ('Giriş tarihinizi (%s) not aldım.').\n" +
                        "- Then ask ONLY for the Check-out Date ('Hangi tarihte otelden çıkış yapmayı planlıyorsunuz / kaç gece konaklayacaksınız?').\n" +
                        "- NEVER call '%s' 'dönüş tarihi' or ask for 'yola çıkış tarihi'!",
                        formatDisplayDate(startDate), formatDisplayDate(startDate), formatDisplayDate(startDate)
                    );
                }
            } else if (startDate == null && endDate != null) {
                if (isFlight) {
                    dateStateInstruction = String.format(
                        "\nSTRICT DATE ACKNOWLEDGMENT RULE (FLIGHT):\n" +
                        "- Return Date (Dönüş Tarihi) is ALREADY KNOWN: '%s'.\n" +
                        "- Departure Date (Gidiş Tarihi) is MISSING.\n" +
                        "- You MUST explicitly acknowledge the Return Date ('Dönüş tarihinizi (%s) not aldım ✈️').\n" +
                        "- Then ask ONLY for the Departure Date ('Hangi tarihte gidiş / yola çıkmayı planlıyorsunuz?').",
                        formatDisplayDate(endDate), formatDisplayDate(endDate)
                    );
                } else {
                    dateStateInstruction = String.format(
                        "\nSTRICT DATE ACKNOWLEDGMENT RULE (HOTEL):\n" +
                        "- Check-out Date (Çıkış Tarihi) is ALREADY KNOWN: '%s'.\n" +
                        "- Check-in Date (Giriş Tarihi) is MISSING.\n" +
                        "- You MUST explicitly acknowledge the Check-out Date ('Çıkış tarihinizi (%s) not aldım.').\n" +
                        "- Then ask ONLY for the Check-in Date ('Hangi tarihte otele giriş yapmayı planlıyorsunuz?').",
                        formatDisplayDate(endDate), formatDisplayDate(endDate)
                    );
                }
            } else if (startDate != null && endDate != null) {
                dateStateInstruction = String.format(
                    "\nSTRICT DATE ACKNOWLEDGMENT RULE:\n" +
                    "- Both dates are already known: %s to %s.\n" +
                    "- Acknowledge the stay/flight period naturally ('%s - %s tarihleri arasındaki konaklamanız/uçuşunuz için...').",
                    formatDisplayDate(startDate), formatDisplayDate(endDate),
                    formatDisplayDate(startDate), formatDisplayDate(endDate)
                );
            }

            boolean hasLocation = isFlight
                    ? (criteria.getDepartureLocation() != null && !criteria.getDepartureLocation().isBlank() && criteria.getArrivalLocation() != null && !criteria.getArrivalLocation().isBlank())
                    : (criteria.getLocationOrHotelName() != null && !criteria.getLocationOrHotelName().isBlank());
            if (!hasLocation && (startDate != null || criteria.getAdultCount() != null || criteria.getPassengerCount() != null)) {
                String datesStr = (startDate != null && endDate != null) ? (startDate + " - " + endDate)
                        : (startDate != null ? startDate.toString() : "");
                String guestsStr = criteria.getAdultCount() != null ? (criteria.getAdultCount() + " kişi")
                        : (criteria.getPassengerCount() != null ? (criteria.getPassengerCount() + " yolcu") : "");

                if (isFlight) {
                    String origin = criteria.getDepartureLocation();
                    String dest = criteria.getArrivalLocation();
                    String routeStr = (origin != null ? origin : "?") + " → " + (dest != null ? dest : "?");
                    knownDetailsInstruction = String.format(
                        "\nKNOWN DETAILS ACKNOWLEDGMENT (FLIGHT):\n" +
                        "- Route: '%s', Dates: '%s', Passengers: '%s'.\n" +
                        "- Structure into two paragraphs separated by \\n\\n:\n" +
                        "  Paragraph 1: '%s rotasında harika bir uçuş planlamak için sabırsızlanıyorum ✈️'\n" +
                        "  Paragraph 2: 'Uçuşunuzu en iyi şekilde organize edebilmem için seyahatinizi **tek yön mü yoksa gidiş-dönüş mü** planladığınızı, **gidiş tarihinizi** ve **yolcu sayısını** belirtebilir misiniz?'",
                        routeStr, datesStr, guestsStr, routeStr
                    );
                } else {
                    knownDetailsInstruction = String.format(
                        "\nKNOWN DETAILS ACKNOWLEDGMENT (HOTEL):\n" +
                        "- Dates: '%s', Guests: '%s', but destination/city is missing.\n" +
                        "- Structure into two paragraphs separated by \\n\\n:\n" +
                        "  Paragraph 1: 'Harika bir tatil planlamak için sabırsızlanıyorum 🏖️'\n" +
                        "  Paragraph 2: 'Konaklamanızı en iyi şekilde organize edebilmem için otele **giriş tarihinizi**, **çıkış tarihinizi** ve kaç **misafir** olarak katılacağınızı öğrenebilir miyim?'",
                        datesStr, guestsStr
                    );
                }
            }
        }

        String ageInstruction = "";
        if (missingFields.contains("çocuk yaşları") || missingFields.contains("bebek yaşları")) {
            boolean isFlightSearch = criteria != null && "FLIGHT_SEARCH".equals(criteria.getSearchType());
            if (isFlightSearch) {
                ageInstruction = "\nSTRICT PRIORITY RULE & WHY (FLIGHT):\n" +
                        "- Structure into two paragraphs separated by \\n\\n:\n" +
                        "- If child age is missing: Paragraph 1 opening, Paragraph 2 asking: '**Çocuklarınızın yaşlarını** paylaşabilir misiniz? Bazı havayolları yaş grubuna göre farklı ücret uyguluyor.'\n" +
                        "- If infant age is missing: Paragraph 1 opening, Paragraph 2 asking: '**Bebeğinizin yaşını** belirtebilir misiniz? Bu bilgi doğru uçuş ve ücret seçeneklerini bulmamıza yardımcı olur.'\n" +
                        "- Do NOT ask for dates, destination, or other fields until child/infant ages are provided.";
            } else {
                ageInstruction = "\nSTRICT PRIORITY RULE & WHY (HOTEL):\n" +
                        "- Structure into two paragraphs separated by \\n\\n:\n" +
                        "- If child age is missing: Paragraph 1 opening, Paragraph 2 asking: '**Çocuklarınızın yaşlarını** da paylaşabilir misiniz? Oteller fiyatlandırmayı genelde yaşa göre hesaplıyor.'\n" +
                        "- If infant age is missing: Paragraph 1 opening, Paragraph 2 asking: '**Bebeğinizin yaşını** (ay/yaş) belirtebilir misiniz? Bu bilgi doğru oda ve fiyat seçeneklerini bulmamıza yardımcı olur.'\n" +
                        "- Do NOT ask for dates, destination, or other fields until child/infant ages are provided.";
            }
        }

        String searchTypeContext = (criteria != null && criteria.getSearchType() != null)
                ? String.format("Active Search Category: %s.", criteria.getSearchType())
                : "Active Search Category: HOTEL_SEARCH.";

        String fieldsCsv = String.join(", ", missingFields);
        String prompt = String.format(
                "You are an expert, hospitable, and warm travel consultant.\n" +
                "The user is planning a trip (%s). The following search criteria are missing: [%s].\n\n" +
                "CRITICAL FORMATTING & MARKDOWN RULES FOR ASKING MISSING INFO:\n" +
                "1. Always split your response into TWO separate paragraphs separated by a blank line (\\n\\n):\n" +
                "   - Paragraph 1: A warm, enthusiastic opening sentence acknowledging their location/trip (e.g. '[Şehir/Bölge]\\'de harika bir tatil planlamak için sabırsızlanıyorum 🏖️' or 'Harika bir seyahat planlamak için sabırsızlanıyorum 😊').\n" +
                "   - Paragraph 2: The main polite question asking for the missing criteria.\n" +
                "2. Always format the missing critical keywords in **BOLD** markdown (e.g., **giriş tarihinizi**, **çıkış tarihinizi**, **misafir sayısını**, **çocuklarınızın yaşlarını**, **gidiş tarihinizi**, **dönüş tarihinizi**, **yolcu sayısını**).\n" +
                "3. Speak naturally like a human travel advisor. DO NOT create technical bulleted summary lists (no 'Şu ana kadar elimizde: ...').\n" +
                "4. STRICT FIELD LIMIT: Ask ONLY for missing destination/city, dates, adult/passenger count, child count/ages, or infant age. NEVER ask for budget, accommodation type, star preference, seat class, etc.\n" +
                "5. Keep emojis to maximum 2-3 per message.\n\n" +
                "Write the question in %s — matching the user's language.%s%s%s%s%s%s\n" +
                "Return ONLY the formatted response text itself, no extra notes.",
                searchTypeContext, fieldsCsv, targetLanguage, userMessageClause(userMessage), poiInstruction, knownDetailsInstruction, ageInstruction, dateStateInstruction, CATEGORY_TERMINOLOGY_CONSTRAINTS
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] AskMissing AI generation failed, using fallback localization: {}", e.getMessage());
        }

        List<String> translatedFields = missingFields.stream()
                .map(field -> {
                    String fieldKey = getFieldKey(field);
                    if (fieldKey != null) {
                        return messageSource.getMessage(fieldKey, null, locale);
                    }
                    return field;
                })
                .collect(Collectors.toList());

        if (translatedFields.size() == 1) {
            if (criteria != null) {
                if ("HOTEL_SEARCH".equals(criteria.getSearchType()) && criteria.getCheckInDate() != null && criteria.getCheckOutDate() == null) {
                    if ("tr".equals(locale.getLanguage())) {
                        return String.format("Giriş tarihinizi (%s) not aldım 😊\n\nKonaklamanızı en iyi şekilde organize edebilmem için otele **çıkış tarihinizi** öğrenebilir miyim?", formatDisplayDate(criteria.getCheckInDate()));
                    }
                }
                if ("FLIGHT_SEARCH".equals(criteria.getSearchType()) && criteria.getDepartureDate() != null && criteria.getReturnDate() == null) {
                    if ("tr".equals(locale.getLanguage())) {
                        return String.format("Gidiş tarihinizi (%s) not aldım ✈️\n\nBu keyifli uçuşu planlayabilmem için seyahatinizi **tek yön mü yoksa gidiş-dönüş mü** olarak planlıyoruz?", formatDisplayDate(criteria.getDepartureDate()));
                    }
                }
            }
            return messageSource.getMessage("ask.missing.single", new Object[]{"**" + translatedFields.get(0) + "**"}, locale);
        } else {
            String joinedFields = translatedFields.stream()
                    .map(f -> "**" + f + "**")
                    .collect(Collectors.joining(", "));
            return messageSource.getMessage("ask.missing.multiple", new Object[]{joinedFields}, locale);
        }
    }

    public String summarize(String intent, String resultsJson, String defaultReply, SearchCriteria criteria, String userMessage, int totalResults, int shownResults) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);

        String childNote = "";
        if (criteria != null && criteria.getChildCount() != null && criteria.getChildCount() > 0) {
            childNote = "\nNote: Some hotels or airlines may have varying age limits for discounts. We can verify the exact policy for your chosen option.";
        }

        String countNote = "";
        if (!"FLIGHT_SEARCH".equalsIgnoreCase(intent)) {
            if (totalResults > shownResults) {
                countNote = String.format("\nFound %d matches for your criteria. Here are the top %d best options:", totalResults, shownResults);
            } else {
                countNote = String.format("\nFound %d matches for your criteria. Here they are:", totalResults);
            }
        }

        String prompt = String.format(
                "You are an expert, hospitable, and professional travel consultant.\n" +
                "The user's travel search has been completed successfully. Here are the search results in JSON format:\n" +
                "Search Type: %s\n" +
                "Results:\n%s\n\n" +
                "Write a warm, polite, professional assistant response introducing these options smoothly.\n" +
                "Adopt a delightful travel consultant tone. Express enthusiasm for helping them plan their trip.\n\n" +
                "CRITICAL PRESENTATION & FORMATTING RULES:\n" +
                "- For Hotel Search (HOTEL_SEARCH):\n" +
                "  Present up to top 5 hotels in the list using a clean, well-formatted Markdown Table:\n\n" +
                "  | 🏨 Otel | ⭐ Yıldız | 📍 Bölge | 💰 Fiyat |\n" +
                "  |---|---|---|---|\n" +
                "  | **[Hotel Name]** | [⭐ repeated N times matching star count, e.g. ⭐⭐⭐⭐] | [City / Region] | [Formatted Price, e.g. 8.629,99 TRY] |\n\n" +
                "  Rules for Hotels:\n" +
                "  - Always bold the hotel name (`**Hotel Name**`).\n" +
                "  - Repeat the ⭐ emoji N times for stars. If stars is 0 or missing, leave empty.\n" +
                "  - Format price with Turkish number format (thousands separator dot, decimal comma) + currency (e.g. `8.629,99 TRY`).\n\n" +
                "  At the end of the hotel table, ALWAYS include a natural closing sentence directing the user to the side panel:\n" +
                "  TR for Hotel: 'Yandaki panelden otellerin detaylarını ve görsellerini inceleyebilirsiniz 😊 İsterseniz bu seçenekleri filtreleyebilirim de.'\n" +
                "  EN for Hotel: 'You can check hotel details and photos in the side panel 😊 Let me know if you\\'d like to filter these options as well.'\n\n" +
                "- For Flight Search (FLIGHT_SEARCH):\n" +
                "  Present up to top 5 flights in the list using a clean, well-formatted Markdown Table:\n\n" +
                "  | ✈️ Havayolu | 🛫 Kalkış | 🛬 Varış | 💰 Fiyat |\n" +
                "  |---|---|---|---|\n" +
                "  | **[Airline Name]** | [Formatted Dep Time, e.g. 11:35] | [Formatted Arr Time, e.g. 13:00] | [Formatted Price, e.g. 1.300,97 TRY] |\n\n" +
                "  Rules for Flights:\n" +
                "  - Always bold the airline name (`**Airline Name**`).\n" +
                "  - Extract clean times (HH:mm format like `11:35`, `00:10`). NEVER display raw ISO datetimes like `2026-08-02T00:10:00`.\n" +
                "  - Format price with Turkish number format (thousands separator dot, decimal comma) + currency (e.g. `1.300,97 TRY`).\n\n" +
                "  At the end of the flight table, ALWAYS include a natural closing sentence:\n" +
                "  TR for Flight: 'Yandaki panelden uçuş detaylarını inceleyebilirsiniz ✈️ İsterseniz bunları en erken saatli veya en uygun fiyatlı seçeneklere göre sıralayabilirim de.'\n" +
                "  EN for Flight: 'You can check flight details in the side panel ✈️ If you\\'d like, I can also sort these by earliest departure or best price.'\n\n" +
                "Include the following context naturally in your response:\n%s%s\n\n" +
                "IMPORTANT CONSTRAINTS:\n" +
                "1. Write the response in %s — matching the user's language.%s\n" +
                "2. ONLY mention facts present in the JSON results (hotel name, stars, location, price, airline, times). If an attribute is missing/null, leave the cell empty without fabricating data.\n" +
                "3. Never invent nicer names for raw system/sandbox data.\n" +
                "4. Return ONLY the assistant response itself, no extra notes.",
                intent, resultsJson, countNote, childNote, targetLanguage, userMessageClause(userMessage)
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] Summarize AI generation failed, using fallback localization: {}", e.getMessage(), e);
        }

        // Fallback summary response if AI is down: return defaultReply
        if (defaultReply != null && !defaultReply.isBlank()) {
            return defaultReply;
        }
        return messageSource.getMessage("search.success.fallback", null, locale);
    }

    public String confirmSelection(Object selectedItem, SearchCriteria criteria) {
        return confirmSelection(selectedItem, criteria, null);
    }

    public String confirmSelection(Object selectedItem, SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = resolveLanguageName(criteria);
        String itemName = "";
        if (selectedItem instanceof com.santsg.tourvisio.dto.HotelSearchResponseItem) {
            itemName = ((com.santsg.tourvisio.dto.HotelSearchResponseItem) selectedItem).getName();
        } else if (selectedItem instanceof com.santsg.tourvisio.dto.FlightSearchResponseItem) {
            itemName = ((com.santsg.tourvisio.dto.FlightSearchResponseItem) selectedItem).getAirline() + " flight";
        }

        String prompt = String.format(
            "You are a warm and helpful travel consultant.\n" +
            "The user has selected '%s' from the search results.\n" +
            "Express enthusiasm for their choice (e.g. '%s harika bir tercih! 🌟') and ask politely if they would like to proceed with booking this option.\n" +
            "Ensure the response is natural and written in %s — the same language the user is writing in.%s\n" +
            "Max 1-2 emojis. Return ONLY the response text.",
            itemName, itemName, targetLanguage, userMessageClause(userMessage));
            
        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] Confirm AI generation failed, using fallback: {}", e.getMessage());
        }
        
        return messageSource.getMessage("confirm.selection", new Object[]{itemName}, locale);
    }

    public String invalidDateRange(String errorType, SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";
        
        String context = "";
        if ("DATE_PAST".equals(errorType)) {
            context = "The user provided a check-in or departure date that is in the past. Explain that they must provide a future date.";
        } else if ("DATE_MISMATCH".equals(errorType)) {
            context = "The user provided a check-out or return date that is before the check-in or departure date. Explain that the return/check-out date must be after the start date.";
        } else if ("DATE_TOO_FAR".equals(errorType)) {
            context = "The user provided a date more than 2 years in the future, which is unrealistic for a travel booking. Explain that they should choose a date within the next 2 years.";
        }
        
        String prompt = String.format(
                "The user is planning a trip, but there is an issue with the dates. %s\n" +
                "Write a polite, helpful response explaining the specific error and asking them to correct the dates. " +
                "Do NOT say 'not found'. Respond directly to the user.\n" +
                "Write the response in the language of this user message: \"%s\" (Target: %s).\n" +
                "Return ONLY the response itself, no extra notes.",
                context, userMessage, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] invalidDateRange AI generation failed: {}", e.getMessage());
        }

        String key = "DATE_PAST".equals(errorType) ? "invalid.date.past"
                : "DATE_TOO_FAR".equals(errorType) ? "invalid.date.too.far"
                : "invalid.date.mismatch";
        return messageSource.getMessage(key, null, locale);
    }

    public String noAdults(SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";

        String prompt = String.format(
                "The user is trying to book a hotel but has indicated 0 adults (only minors). " +
                "Write a polite response explaining that hotel reservations legally require at least one accompanying adult guest. " +
                "Write the response in the language of this user message: \"%s\" (Target: %s).\n" +
                "Return ONLY the response itself, no extra notes.",
                userMessage, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] noAdults AI generation failed: {}", e.getMessage());
        }

        return messageSource.getMessage("error.no.adults", null, locale);
    }

    /**
     * Negatif yolcu/misafir sayısı (ör. "-3 kişi") ya da izin verilen üst sınırı
     * (otelde 8, uçakta 9) aşan bir sayı girildiğinde çağrılır. Bilinçli olarak
     * serbest metinli bir yapay zeka çağrısı YAPMIYORUZ — anlamsız bir sayı,
     * modelin şaşırıp beklenmedik/alakasız bir dilde cevap vermesine yol
     * açabiliyordu; bunun yerine sabit, yerelleştirilmiş bir mesaj döndürülür.
     */
    public String invalidGuestCount(String errorType, SearchCriteria criteria) {
        Locale locale = resolveLocale(criteria);
        String key;
        Object[] args = null;
        switch (errorType) {
            case "NEGATIVE_COUNT":
                key = "error.negative.count";
                break;
            case "TOO_MANY_GUESTS":
                key = "error.too.many.guests";
                args = new Object[]{8};
                break;
            case "TOO_MANY_PASSENGERS":
                key = "error.too.many.passengers";
                args = new Object[]{9};
                break;
            case "TOO_MANY_ROOMS":
                key = "error.too.many.rooms";
                break;
            default:
                key = "error.negative.count";
        }
        return messageSource.getMessage(key, args, locale);
    }

    public String noResultsFound(SearchCriteria criteria, String userMessage) {
        return noResultsFound(criteria, userMessage, null);
    }

    public String noResultsFound(SearchCriteria criteria, String userMessage, java.util.List<String> suggestedDates) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";

        // Uçuş aramalarında konum/tarih bilgisi locationOrHotelName/checkInDate'de değil,
        // departureLocation+arrivalLocation / departureDate+returnDate'de tutulur. Bunu
        // ayırt etmeden hep otel alanlarını okumak, uçuş aramalarında modele "Location: null"
        // göndermeye yol açıyordu — model de boş bırakmak yerine rastgele bir şehir/tarih
        // uyduruyordu (ör. hiç aranmamış "Bodrum").
        boolean isFlightSearch = criteria != null && "FLIGHT_SEARCH".equals(criteria.getSearchType());
        String location;
        String checkIn;
        String checkOut;
        if (isFlightSearch) {
            String departure = criteria.getDepartureLocation();
            String arrival = criteria.getArrivalLocation();
            location = (departure != null || arrival != null)
                    ? (departure != null ? departure : "?") + " → " + (arrival != null ? arrival : "?")
                    : "the selected destination";
            checkIn = criteria.getDepartureDate() != null ? criteria.getDepartureDate().toString() : "?";
            checkOut = criteria.getReturnDate() != null ? criteria.getReturnDate().toString() : "?";
        } else {
            location = criteria != null ? criteria.getLocationOrHotelName() : "the selected destination";
            checkIn = criteria != null && criteria.getCheckInDate() != null ? criteria.getCheckInDate().toString() : "?";
            checkOut = criteria != null && criteria.getCheckOutDate() != null ? criteria.getCheckOutDate().toString() : "?";
        }
        String adults = criteria != null && criteria.getAdultCount() != null ? String.valueOf(criteria.getAdultCount()) : "?";
        boolean hasSuggestions = suggestedDates != null && !suggestedDates.isEmpty();
        String suggestedDatesText = hasSuggestions ? String.join(", ", suggestedDates) : null;
        // Sadece yetişkin sayısını yazınca, kullanıcı bir önceki turdan "sticky" kalmış
        // (o mesajda hiç bahsedilmemiş) çocuk/bebek sayısının hâlâ aramaya dahil
        // olduğunu fark edemiyordu — cevap "3 yetişkin için..." derken aslında arka
        // planda 3 bebek de aranıyor olabiliyordu. Şimdi tam misafir kompozisyonu
        // (yetişkin+çocuk+bebek) modele veriliyor ki cevap gerçek aramayı yansıtsın.
        String guestsDescription = describeGuestComposition(criteria);

        String prompt = String.format(
                "The user searched for a trip (Location: %s, Dates: %s to %s, Guests: %s) but no results were found in the system.\n" +
                "Write a polite response. Start by briefly restating the key criteria (location, dates, guests) you understood from the user's request, then explain that no hotels or flights were found matching those exact criteria. " +
                "Never give a bare 'not found' message with zero context. IMPORTANT: the Guests value already includes " +
                "children/infants carried over from earlier in the conversation even if the user's latest message didn't " +
                "mention them — state the FULL guest composition honestly (e.g. \"3 adults, 3 infants\"), do not silently " +
                "drop the children/infants just because this message only talked about adults. " +
                "NEVER invent or guess a value that isn't given above — if Location, a date, or Guests shows as unspecified/\"?\", " +
                "phrase the sentence to omit that detail rather than making one up (e.g. do not state a city or date that wasn't provided).%s\n" +
                "Write the response in the language of this user message: \"%s\" (Target: %s).\n" +
                "Return ONLY the response itself, no extra notes.",
                location, checkIn, checkOut, guestsDescription,
                hasSuggestions
                        ? " The following nearby dates ARE available for this location, so suggest the user try one of them instead: " + suggestedDatesText + "."
                        : "",
                userMessage, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] noResultsFound AI generation failed: {}", e.getMessage());
        }

        boolean isFlight = criteria != null && "FLIGHT_SEARCH".equals(criteria.getSearchType());
        String msgKey = isFlight ? "flight.search.no.results" : "hotel.search.no.results";
        String withDatesKey = isFlight ? "flight.search.no.results.with.dates" : "hotel.search.no.results.with.dates";
        String defaultMsg = hasSuggestions
                ? messageSource.getMessage(withDatesKey, new Object[]{suggestedDatesText}, locale)
                : messageSource.getMessage(msgKey, null, locale);
        if (criteria != null) {
            String locationParam = criteria.getLocationOrHotelName() != null ? criteria.getLocationOrHotelName() : "?";
            java.time.LocalDate startDate = isFlight ? criteria.getDepartureDate() : criteria.getCheckInDate();
            java.time.LocalDate endDate = isFlight ? criteria.getReturnDate() : criteria.getCheckOutDate();
            String datesParam = formatDisplayDate(startDate) + " - " + formatDisplayDate(endDate);
            String adultsParam = criteria.getAdultCount() != null ? criteria.getAdultCount().toString() : "?";
            String childrenParam = criteria.getChildCount() != null ? criteria.getChildCount().toString() : "0";
            String infantsParam = criteria.getInfantCount() != null ? criteria.getInfantCount().toString() : "0";

            String details = messageSource.getMessage("criteria.understood",
                new Object[]{locationParam, datesParam, adultsParam, childrenParam, infantsParam}, locale);
            return defaultMsg + " (" + details + ")";
        }
        return defaultMsg;
    }

    /**
     * "3 yetişkin, 2 çocuk, 1 bebek" tarzında tam misafir kompozisyonu metni üretir.
     * Sadece yetişkin sayısını gösteren cevaplar, önceki turdan "sticky" kalmış
     * (bu mesajda hiç bahsedilmemiş) çocuk/bebek sayısının hâlâ aramaya dahil
     * olduğunu kullanıcıdan gizlemiş oluyordu.
     */
    private String describeGuestComposition(SearchCriteria criteria) {
        if (criteria == null) return "?";
        List<String> parts = new java.util.ArrayList<>();
        if (criteria.getAdultCount() != null) parts.add(criteria.getAdultCount() + " adults");
        if (criteria.getChildCount() != null && criteria.getChildCount() > 0) parts.add(criteria.getChildCount() + " children");
        if (criteria.getInfantCount() != null && criteria.getInfantCount() > 0) parts.add(criteria.getInfantCount() + " infants");
        return parts.isEmpty() ? "?" : String.join(", ", parts);
    }

    public String noMoreResults(SearchCriteria criteria, String userMessage) {
        Locale locale = resolveLocale(criteria);
        String targetLanguage = (criteria != null && criteria.getPreferredLanguage() != null) ? criteria.getPreferredLanguage() : "English";

        String prompt = String.format(
                "The user is asking for more search results, but there are no further options available in the current search. " +
                "Write a polite response explaining that there are no additional results left to show for their current criteria, and suggest they might want to change their dates, location, or other preferences to see different options.\n" +
                "Write the response in the language of this user message: \"%s\" (Target: %s).\n" +
                "Return ONLY the response itself, no extra notes.",
                userMessage, targetLanguage
        );

        try {
            String aiResponse = geminiClient.generate(prompt);
            if (isValidResponse(aiResponse)) {
                return aiResponse.trim();
            }
        } catch (Exception e) {
            log.warn("[ResponseAgent] noMoreResults AI generation failed: {}", e.getMessage());
        }

        // Fallback to the regular no results found message if AI fails
        return noResultsFound(criteria, userMessage);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static final java.time.format.DateTimeFormatter DISPLAY_DATE_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private String formatDisplayDate(java.time.LocalDate date) {
        return date != null ? date.format(DISPLAY_DATE_FORMAT) : "?";
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank())
            return s;
        return s.substring(0, 1).toUpperCase(java.util.Locale.forLanguageTag("tr-TR"))
                + s.substring(1).toLowerCase(java.util.Locale.forLanguageTag("tr-TR"));
    }

    /**
     * Prompt'a kullanıcının ham mesajını ekleyerek dil talimatını somutlaştırır.
     * Sadece "hedef dil" adını söylemek (özellikle küçük/ücretsiz modellerde)
     * yetersiz kalabiliyor; mesajın kendisini de göstermek modelin doğru dili
     * seçmesini büyük ölçüde güçlendiriyor.
     */
    private String userMessageClause(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return "";
        }
        return " The user's exact message was: \"" + userMessage.replace("\"", "\\\"") + "\".";
    }

    private static final java.util.regex.Pattern JSON_KEY_VALUE_PATTERN =
            java.util.regex.Pattern.compile("\"[\\w.]+\"\\s*:\\s*\"");

    private boolean isValidResponse(String response) {
        if (response == null || response.trim().isEmpty()
                || response.trim().startsWith("[MOCK]")
                || response.contains("Gemini service could not be reached")) {
            return false;
        }
        // Ücretsiz yedek modeller ara sıra cevaba alakasız ham içerik (ör. bir
        // i18n JSON dosyasının içeriği: {"searchAgainButton": "...", ...})
        // karıştırıyor. Doğal dil cevabında art arda birden fazla JSON
        // key-value çifti görülmesi beklenmez — görülürse cevabı reddedip
        // şablon tabanlı güvenli mesaja düşüyoruz.
        java.util.regex.Matcher matcher = JSON_KEY_VALUE_PATTERN.matcher(response);
        int matchCount = 0;
        while (matcher.find()) {
            matchCount++;
            if (matchCount >= 2) {
                log.warn("[ResponseAgent] AI cevabında JSON benzeri içerik tespit edildi, reddediliyor: {}",
                        response.length() > 200 ? response.substring(0, 200) + "..." : response);
                return false;
            }
        }
        return true;
    }

    private Locale resolveLocale(SearchCriteria criteria) {
        return com.santsg.tourvisio.util.LocaleResolver.resolveLocale(criteria);
    }

    /**
     * Locale kodunu (veya ülke adını) AI prompt'ları için okunabilir bir dil
     * adına çevirir. criteria.getPreferredLanguage() ham haliyle (ör. "Turkey")
     * prompt'a verildiğinde model karışabiliyor; bunun yerine dil adını kullanıyoruz.
     */
    private String resolveLanguageName(SearchCriteria criteria) {
        return com.santsg.tourvisio.util.LocaleResolver.resolveLanguageName(criteria);
    }

    private String getFieldKey(String field) {
        if (field == null) return null;
        switch (field.trim()) {
            case "konum veya otel adı":
            case "locationOrHotelName": return "field.locationOrHotelName";
            case "giriş tarihi":
            case "checkInDate": return "field.checkInDate";
            case "çıkış tarihi":
            case "checkOutDate": return "field.checkOutDate";
            case "yetişkin sayısı":
            case "adultCount": return "field.adultCount";
            case "çocuk sayısı":
            case "childCount": return "field.childCount";
            case "çocuk yaşları":
            case "childAges": return "field.childAges";
            case "bebek sayısı":
            case "infantCount": return "field.infantCount";
            case "bebek yaşları":
            case "infantAges": return "field.infantAges";
            case "para birimi":
            case "currency": return "field.currency";
            case "kalkış noktası":
            case "departureLocation": return "field.departureLocation";
            case "varış noktası":
            case "arrivalLocation": return "field.arrivalLocation";
            case "gidiş tarihi":
            case "departureDate": return "field.departureDate";
            case "yolcu sayısı":
            case "passengerCount": return "field.passengerCount";
            case "tek yön / gidiş-dönüş":
            case "tripType": return "field.tripType";
            case "dönüş tarihi":
            case "returnDate": return "field.returnDate";
            default: return null;
        }
    }
}
