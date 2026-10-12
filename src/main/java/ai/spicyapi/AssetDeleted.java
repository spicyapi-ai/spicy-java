package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The outcome of deleting an asset.
 *
 * <p>Deleting an unknown or already deleted asset, or one in a library this key does not reach,
 * returns the same result, so the call is safe to repeat.
 *
 * @param id      the asset that was addressed
 * @param deleted always true
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetDeleted(String id, boolean deleted) {
}
