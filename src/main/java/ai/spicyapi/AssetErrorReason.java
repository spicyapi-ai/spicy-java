package ai.spicyapi;

import java.util.Optional;

/**
 * Machine-readable reasons the asset library attaches to a failed response as {@code data.reason}.
 *
 * <p>Read the raw value with {@link SpicyApiException#reason()}, which carries any string the
 * service sends, unchanged; {@link SpicyApiException#assetErrorReason()} maps it onto this enum.
 * The service may add reasons later, so treat the set as open: a value that maps to nothing here is
 * best handled like an absent reason, which is why the mapping returns an {@link Optional} instead
 * of a fallback constant.
 *
 * <p>The constants match the contract's {@code ErrorEnvelope.data.reason} enum, and a test
 * compares the two.
 */
public enum AssetErrorReason {

    /** HTTP 400: the library opens after the owner's first top-up. */
    ASSET_LIBRARY_LOCKED("asset_library_locked"),
    /** HTTP 400: the allowance's storage or item count is used up. */
    ASSET_LIBRARY_FULL("asset_library_full"),
    /** HTTP 400: the file is above the plan's per-file limit. */
    ASSET_FILE_TOO_LARGE("asset_file_too_large"),
    /** HTTP 429: today's (UTC) allowance of new assets is used up; see {@code Retry-After}. */
    ASSET_DAILY_LIMIT("asset_daily_limit"),
    /** HTTP 503: storage is temporarily full across the platform. */
    ASSET_STORAGE_FULL("asset_storage_full"),
    /** HTTP 403: the key's team role does not allow this change. */
    ASSET_PERMISSION_DENIED("asset_permission_denied"),
    /** HTTP 403: this key or workspace cannot reach that library. */
    ASSET_WORKSPACE_FORBIDDEN("asset_workspace_forbidden"),
    /**
     * HTTP 409: the library already has a character with that name, ignoring case. Characters are
     * named in the console, so the read-only API does not return it today.
     */
    ASSET_ENTITY_NAME_TAKEN("asset_entity_name_taken");

    private final String wireValue;

    AssetErrorReason(String wireValue) {
        this.wireValue = wireValue;
    }

    /**
     * The value as it appears in {@code data.reason}.
     *
     * @return the wire value
     */
    public String wireValue() {
        return wireValue;
    }

    /**
     * Maps a {@code data.reason} value.
     *
     * @param value the raw reason, may be {@code null}
     * @return the matching constant, or empty for {@code null} and for any value this release does
     *         not know
     */
    public static Optional<AssetErrorReason> fromWireValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (AssetErrorReason reason : values()) {
            if (reason.wireValue.equals(value)) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }
}
