package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Which library the key reaches, and what it may do there.
 *
 * <p>A key created in your personal workspace reaches your personal library. A key created in a
 * team workspace, by any member, reaches that team's shared library, and what it may change follows
 * the current role of the member who created it.
 *
 * @param workspaceId the workspace whose library this is; empty for an account that has no personal
 *                    workspace yet
 * @param personal    whether this is a personal library
 * @param role        {@code owner}, {@code admin}, {@code developer}, {@code billing} or
 *                    {@code readonly}; always {@code owner} for a personal library
 * @param canWrite    whether new assets can be saved here (owner, admin and developer)
 * @param canTopUp    whether the caller can see and raise the owner's allowance: only the owner.
 *                    When false, {@link AssetUsage#lifetimeTopup()} and
 *                    {@link AssetUsage#topupToNextTier()} are empty
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetScope(String workspaceId, boolean personal, String role, boolean canWrite, boolean canTopUp) {
}
