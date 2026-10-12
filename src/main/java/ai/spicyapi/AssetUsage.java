package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Optional;

/**
 * Plan, usage and limits of the allowance behind the key's library.
 *
 * <p>Storage belongs to the owner: the owner's personal library and every team library they own
 * share one allowance, and these figures cover all of it. A plan with every limit at 0 cannot save
 * new assets; assets already saved stay usable.
 *
 * @param tier                the current plan
 * @param stored              how many assets are stored and how many bytes they take; the wire
 *                            name is {@code usage}
 * @param limits              the limits that apply now
 * @param full                true when no new asset fits, including on plans where saving is
 *                            closed
 * @param nextTier            the next plan with more storage or items; {@code null} on the highest
 *                            plan
 * @param topupToNextTierUsd  how much more to top up, in USD, to reach {@code nextTier}; omitted
 *                            when {@link AssetScope#canTopUp()} is false
 * @param lifetimeTopupUsd    the owner's lifetime top-ups in USD; omitted when
 *                            {@link AssetScope#canTopUp()} is false
 * @param tiers               the full plan table
 * @param dailyCreateLimit    new assets allowed per allowance per UTC day
 * @param createdToday        new assets created today, UTC
 * @param scope               which library the key reaches and what it may do there
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetUsage(AssetPlan tier, @JsonProperty("usage") Stored stored, Limits limits, boolean full,
                         AssetPlan nextTier, String topupToNextTierUsd, String lifetimeTopupUsd,
                         List<AssetPlan> tiers, int dailyCreateLimit, int createdToday, AssetScope scope) {

    public AssetUsage {
        tiers = Internal.emptyIfNull(tiers);
    }

    /**
     * What is stored now.
     *
     * @param count how many assets are stored
     * @param bytes how many bytes they take
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Stored(long count, long bytes) {
    }

    /**
     * The limits that apply now.
     *
     * @param tier           the plan key these limits come from
     * @param maxCount       how many assets fit
     * @param maxBytes       total storage in bytes
     * @param maxFileBytes   the largest single file accepted
     * @param globalMaxBytes the platform-wide ceiling; 0 disables it
     * @param nameMaxChars   the longest asset name, 80
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Limits(String tier, long maxCount, long maxBytes, long maxFileBytes, long globalMaxBytes,
                         int nameMaxChars) {
    }

    /**
     * The next plan, when there is one.
     *
     * @return the next plan, or empty on the highest plan
     */
    public Optional<AssetPlan> next() {
        return Optional.ofNullable(nextTier);
    }

    /**
     * How much more to top up to reach the next plan, when the caller may see it.
     *
     * @return the amount in USD as a decimal string, or empty when {@link AssetScope#canTopUp()} is
     *         false or there is no next plan
     */
    public Optional<String> topupToNextTier() {
        return Optional.ofNullable(topupToNextTierUsd);
    }

    /**
     * The owner's lifetime top-ups, when the caller may see them.
     *
     * @return the amount in USD as a decimal string, or empty when {@link AssetScope#canTopUp()}
     *         is false
     */
    public Optional<String> lifetimeTopup() {
        return Optional.ofNullable(lifetimeTopupUsd);
    }
}
