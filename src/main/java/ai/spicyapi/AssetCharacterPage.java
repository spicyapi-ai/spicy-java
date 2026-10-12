package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One page of characters, newest first.
 *
 * <p>To continue, repeat the same {@link AssetCharacterFilter} with only the cursor moved, or let
 * {@link SpicyAssetCharacters#listAll(AssetCharacterFilter)} do the walk.
 *
 * @param items      this page's characters
 * @param nextCursor the cursor for the next page; absent on the last page. It is opaque: pass it
 *                   back unchanged and never construct one
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetCharacterPage(List<AssetCharacter> items, String nextCursor) {

    public AssetCharacterPage {
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
