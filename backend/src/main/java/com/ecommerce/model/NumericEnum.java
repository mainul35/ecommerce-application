package com.ecommerce.model;

/**
 * Contract for enums persisted as numeric codes in the database.
 *
 * Convention: every status-like enum in the system implements this interface
 * and is stored as SMALLINT. Codes are part of the persisted contract -
 * NEVER renumber an existing constant; only append new ones.
 *
 * JSON serialization is unaffected: DTOs still expose the enum NAME so the
 * API stays human-readable - the numeric code exists only at the DB boundary
 * (see config/converter/NumericEnumConverters).
 */
public interface NumericEnum {

    /** Stable numeric code stored in the database. */
    int getCode();

    /** Resolve an enum constant from its persisted code. */
    static <E extends Enum<E> & NumericEnum> E fromCode(Class<E> type, int code) {
        for (E constant : type.getEnumConstants()) {
            if (constant.getCode() == code) {
                return constant;
            }
        }
        throw new IllegalArgumentException(
                "Unknown " + type.getSimpleName() + " code: " + code);
    }
}
