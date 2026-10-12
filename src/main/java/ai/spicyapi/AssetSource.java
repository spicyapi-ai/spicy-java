package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Where a saved {@link Asset} came from.
 *
 * <p>An unrecognised value still deserializes, as {@link #UNKNOWN}, so that a source the service
 * adds later cannot make an older client fail to read a whole page of assets. {@link #UNKNOWN} is
 * never sent.
 */
public enum AssetSource {

    /** One output of a successful task. */
    TASK_OUTPUT("task_output"),
    /** A committed upload. */
    UPLOAD("upload"),
    /** A public link the service downloaded once. */
    URL("url"),
    /** A value this client release does not know. Never sent by a client. */
    UNKNOWN("");

    private final String wireValue;

    AssetSource(String wireValue) {
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
    public static AssetSource fromWireValue(String value) {
        if (value == null) {
            return null;
        }
        for (AssetSource source : values()) {
            if (source.wireValue.equals(value)) {
                return source;
            }
        }
        return UNKNOWN;
    }
}
