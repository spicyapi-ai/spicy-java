package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Order of an asset listing.
 *
 * <p><b>A cursor only works with the sort it came from.</b> Continue a listing with the same sort
 * it started with; a {@code nextCursor} sent with a different sort is refused by the service with
 * HTTP 400, which this client surfaces as it is and does not retry.
 *
 * <p>Unlike the response-side enums, this one has no fallback constant: it is only ever sent, and
 * {@link #fromWireValue(String)} refuses a value the contract does not list, so a typo fails
 * locally instead of costing a round trip.
 */
public enum AssetSort {

    /** Newest first. The service's default. */
    RECENT("recent"),
    /** Oldest first. */
    OLDEST("oldest"),
    /** By name, A to Z, ignoring case. */
    NAME("name"),
    /** Largest first. */
    SIZE("size");

    private final String wireValue;

    AssetSort(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * The value as it appears on the wire.
     *
     * @return the wire value
     */
    @JsonValue
    public String wireValue() {
        return wireValue;
    }

    /**
     * Resolves a wire value.
     *
     * @param value one of {@code recent}, {@code oldest}, {@code name} or {@code size}; case is
     *              ignored and surrounding whitespace trimmed
     * @return the matching constant
     * @throws IllegalArgumentException when the value is blank or not one of those four
     */
    public static AssetSort fromWireValue(String value) {
        String normalized = Internal.requireText(value, "sort").toLowerCase(Locale.ROOT);
        for (AssetSort sort : values()) {
            if (sort.wireValue.equals(normalized)) {
                return sort;
            }
        }
        throw new IllegalArgumentException(
                "unsupported asset sort: " + value + "; use recent, oldest, name or size");
    }
}
