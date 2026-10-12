package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What kind of file a saved {@link Asset} is.
 *
 * <p>An unrecognised value still deserializes, as {@link #UNKNOWN}, so that a kind the service adds
 * later cannot make an older client fail to read a whole page of assets. {@link #UNKNOWN} is never
 * sent: {@link AssetFilter#withKind(AssetKind)} refuses it.
 */
public enum AssetKind {

    /** A still image. */
    IMAGE("image"),
    /** A video clip. */
    VIDEO("video"),
    /** An audio clip. */
    AUDIO("audio"),
    /** A value this client release does not know. Never sent by a client. */
    UNKNOWN("");

    private final String wireValue;

    AssetKind(String wireValue) {
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
     * Maps a wire value, tolerating values added after this release.
     *
     * @param value the wire value, may be {@code null}
     * @return the matching constant, {@link #UNKNOWN} for anything unrecognised, or {@code null}
     *         for {@code null}
     */
    @JsonCreator
    public static AssetKind fromWireValue(String value) {
        if (value == null) {
            return null;
        }
        for (AssetKind kind : values()) {
            if (kind.wireValue.equals(value)) {
                return kind;
            }
        }
        return UNKNOWN;
    }
}
