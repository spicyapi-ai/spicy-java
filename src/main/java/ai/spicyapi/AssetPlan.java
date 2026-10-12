package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One storage plan of the asset library.
 *
 * <p>Plans unlock automatically with the owner's lifetime top-ups and never go down. Byte values
 * use binary units (500 MB is 524288000 bytes).
 *
 * @param id           lowercase plan key, for example {@code free}, {@code plus} or {@code pro}
 * @param name         the plan's display name
 * @param minTopupUsd  lifetime top-ups in USD that unlock this plan, as a decimal string
 * @param maxBytes     total storage in bytes; 0 means saving is closed on this plan
 * @param maxCount     how many assets the plan holds
 * @param maxFileBytes the largest single file the plan accepts
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetPlan(String id, String name, String minTopupUsd, long maxBytes, long maxCount,
                        long maxFileBytes) {
}
