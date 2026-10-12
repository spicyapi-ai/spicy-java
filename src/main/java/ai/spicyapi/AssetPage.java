package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One page of saved assets.
 *
 * <p>To continue, repeat the same {@link AssetFilter} with only the cursor moved:
 * {@code filter.withCursor(page.nextCursor())}. A cursor only works with the sort it came from, so
 * changing the sort between pages is refused with HTTP 400.
 * {@link SpicyAssets#listAll(AssetFilter)} does the walk for you.
 *
 * @param items      this page's assets
 * @param nextCursor the cursor for the next page; absent on the last page. It is opaque: pass it
 *                   back unchanged and never construct one
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetPage(List<Asset> items, String nextCursor) {

    public AssetPage {
        items = Internal.emptyIfNull(items);
    }

    /**
     * Whether another page follows.
     *
     * @return true when {@link #nextCursor()} is present
     */
    public boolean hasMore() {
        return nextCursor != null && !nextCursor.isEmpty();
    }
}
