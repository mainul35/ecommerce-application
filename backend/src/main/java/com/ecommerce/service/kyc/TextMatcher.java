package com.ecommerce.service.kyc;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Fuzzy matching between user-typed values and noisy OCR output.
 * Pure functions, no state - deliberately simple and explainable so a
 * reviewer can reason about why a score passed or failed.
 */
public final class TextMatcher {

    private TextMatcher() {
    }

    /**
     * Name score: token-set overlap with per-token typo tolerance.
     * The OCR side is a whole document, so we ask "does every name token
     * appear (approximately) somewhere in the document?".
     */
    public static BigDecimal nameInText(String typedName, String ocrText) {
        Set<String> nameTokens = tokens(typedName);
        if (nameTokens.isEmpty() || ocrText == null || ocrText.isBlank()) {
            return BigDecimal.ZERO;
        }
        Set<String> docTokens = tokens(ocrText);
        int found = 0;
        for (String token : nameTokens) {
            if (containsApprox(docTokens, token)) {
                found++;
            }
        }
        return ratio(found, nameTokens.size());
    }

    /**
     * Address score: share of significant typed-address tokens present in the
     * OCR'd bill. House/flat punctuation and ordering vary wildly between a
     * form field and a printed bill, so token recall beats string distance.
     */
    public static BigDecimal addressInText(String typedAddress, String ocrText) {
        Set<String> addressTokens = tokens(typedAddress);
        // Drop ubiquitous filler that inflates scores without verifying anything.
        addressTokens.removeAll(Set.of("road", "rd", "street", "st", "house", "flat",
                "no", "block", "area", "p", "o", "po", "ps", "dist", "district"));
        if (addressTokens.isEmpty() || ocrText == null || ocrText.isBlank()) {
            return BigDecimal.ZERO;
        }
        Set<String> docTokens = tokens(ocrText);
        int found = 0;
        for (String token : addressTokens) {
            if (containsApprox(docTokens, token)) {
                found++;
            }
        }
        return ratio(found, addressTokens.size());
    }

    // ------------------------------------------------------------------

    private static Set<String> tokens(String text) {
        if (text == null) {
            return new LinkedHashSet<>();
        }
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")               // strip diacritics
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", " ");       // punctuation -> spaces
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(normalized.split("\\s+"))
                .filter(t -> t.length() >= 2 || t.matches("\\d"))
                .forEach(result::add);
        return result;
    }

    /** True when the token (or a 1-edit-distance variant of it) is in the set. */
    private static boolean containsApprox(Set<String> haystack, String needle) {
        if (haystack.contains(needle)) {
            return true;
        }
        // OCR typo tolerance only for words long enough to make 1 edit safe.
        if (needle.length() < 4) {
            return false;
        }
        for (String candidate : haystack) {
            if (Math.abs(candidate.length() - needle.length()) <= 1
                    && editDistanceAtMostOne(candidate, needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean editDistanceAtMostOne(String a, String b) {
        if (a.equals(b)) {
            return true;
        }
        if (Math.abs(a.length() - b.length()) > 1) {
            return false;
        }
        int i = 0;
        int j = 0;
        int edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) {
                i++;
                j++;
                continue;
            }
            if (++edits > 1) {
                return false;
            }
            if (a.length() > b.length()) {
                i++;                 // deletion in a
            } else if (a.length() < b.length()) {
                j++;                 // insertion in a
            } else {
                i++;                 // substitution
                j++;
            }
        }
        edits += (a.length() - i) + (b.length() - j);
        return edits <= 1;
    }

    private static BigDecimal ratio(int found, int total) {
        return BigDecimal.valueOf(found)
                .divide(BigDecimal.valueOf(total), 3, RoundingMode.HALF_UP);
    }
}
