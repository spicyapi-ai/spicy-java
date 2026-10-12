package ai.spicyapi;

/**
 * Filter for {@link SpicyAssetCharacters#list(AssetCharacterFilter)}. Any {@code null} field is
 * omitted.
 *
 * @param assetId only the characters that contain this asset, as {@code ast_...} or
 *                {@code spicy://a/ast_...}; the reference form is unwrapped before it is sent
 * @param cursor  the {@link AssetCharacterPage#nextCursor()} of the previous page, passed through
 *                unchanged
 * @param limit   page size; the contract's floor is 1 and its default is 50
 */
public record AssetCharacterFilter(String assetId, String cursor, Integer limit) {

    public AssetCharacterFilter {
        assetId = assetId == null ? null : Internal.bareAssetId(assetId, "assetId");
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
    }

    /**
     * A filter that selects every character.
     *
     * @return a filter with every field unset
     */
    public static AssetCharacterFilter all() {
        return new AssetCharacterFilter(null, null, null);
    }

    /**
     * @param value the asset the characters must contain, as {@code ast_...} or
     *              {@code spicy://a/ast_...}
     * @return a copy with the asset set
     * @throws IllegalArgumentException when {@code value} is blank or an empty reference
     */
    public AssetCharacterFilter withAssetId(String value) {
        return new AssetCharacterFilter(value, cursor, limit);
    }

    /**
     * Moves to the next page, keeping every other field as it is.
     *
     * @param value the {@link AssetCharacterPage#nextCursor()} of the page just read, unchanged
     * @return a copy pointing at the next page
     */
    public AssetCharacterFilter withCursor(String value) {
        return new AssetCharacterFilter(assetId, value, limit);
    }

    /**
     * @param value the page size, at least 1
     * @return a copy with the page size set
     * @throws IllegalArgumentException when {@code value} is below 1
     */
    public AssetCharacterFilter withLimit(int value) {
        return new AssetCharacterFilter(assetId, cursor, value);
    }

    String toQuery() {
        StringBuilder query = new StringBuilder();
        Internal.appendParameter(query, "assetId", assetId);
        Internal.appendParameter(query, "cursor", cursor);
        Internal.appendParameter(query, "limit", limit == null ? null : limit.toString());
        return query.length() == 0 ? "" : "?" + query;
    }
}
