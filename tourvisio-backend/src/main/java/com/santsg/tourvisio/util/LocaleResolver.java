package com.santsg.tourvisio.util;

import com.santsg.tourvisio.chat.SearchCriteria;

import java.util.Locale;

/**
 * {@link SearchCriteria#getPreferredLanguage()} alanını (ülke adı veya dil
 * kodu olabilir, ör. "Turkey", "tr", "Germany") gerçek bir {@link Locale}'e
 * çevirir. ResponseAgent, FlightSearchService ve HotelSearchService arasında
 * paylaşılır ki dil eşleştirme mantığı tek yerde kalsın.
 */
public final class LocaleResolver {

    private LocaleResolver() {
    }

    public static Locale resolveLocale(SearchCriteria criteria) {
        if (criteria == null) {
            return Locale.ENGLISH;
        }
        String lang = criteria.getPreferredLanguage();
        if (lang == null || lang.isBlank()) {
            return Locale.ENGLISH;
        }
        String normalized = lang.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("tr") || normalized.startsWith("tr-") || normalized.startsWith("tr_")
                || normalized.contains("turk")) {
            return Locale.forLanguageTag("tr-TR");
        }
        if (normalized.equals("de") || normalized.startsWith("de-") || normalized.startsWith("de_")
                || normalized.contains("german") || normalized.contains("deutsch")) {
            return Locale.GERMAN;
        }
        if (normalized.equals("ru") || normalized.startsWith("ru-") || normalized.startsWith("ru_")
                || normalized.contains("russia")) {
            return Locale.forLanguageTag("ru-RU");
        }
        return Locale.ENGLISH;
    }

    public static String resolveLanguageName(SearchCriteria criteria) {
        Locale locale = resolveLocale(criteria);
        if ("tr".equals(locale.getLanguage())) return "Turkish";
        if ("de".equals(locale.getLanguage())) return "German";
        if ("ru".equals(locale.getLanguage())) return "Russian";
        return "English";
    }
}
