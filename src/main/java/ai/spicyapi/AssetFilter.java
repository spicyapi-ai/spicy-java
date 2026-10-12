package ai.spicyapi;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Filter and order for {@link SpicyAssets#list(AssetFilter)}. Any {@code null} field is omitted,
 * and so is an empty source list.
 *
 * <p>Page by repeating the filter unchanged and moving only the cursor. <b>A cursor only works with
 * the sort it came from</b>: a {@code nextCursor} sent with a different {@code sort} is refused with
 * HTTP 400. {@link SpicyAssets#listAll(AssetFilter)} keeps every field, the sort included, on every
 * page.
 *
 * @param kind         only assets of this kind
 * @param sources      only assets from these sources; several are sent comma-separated
 * @param search       a case-insensitive substring of the asset name, sent as {@code q}; the
 *                     contract allows up to 80 characters
 * @param favorite     {@code true} lists only favorites, {@code false} only assets that are not
 *                     favorites, {@code null} both. {@code false} is a filter of its own and is sent
 * @param collectionId only the assets in this folder (a collection of the same library). Folders
 *                     are created in the console; an id outside the key's library is answered with
 *                     HTTP 404
 * @param entityId     only the images of this character (see {@link SpicyAssets#characters()}); an
 *                     unknown id gives an empty page
 * @param sort         the order; {@code null} leaves the service default, {@link AssetSort#RECENT}
 * @param cursor       the {@link AssetPage#nextCursor()} of the previous page, passed through
 *                     unchanged
 * @param limit        page size; the contract's floor is 1 and its default is 100
 */
public record AssetFilter(AssetKind kind, List<AssetSource> sources, String search, Boolean favorite,
                          String collectionId, String entityId, AssetSort sort, String cursor, Integer limit) {

    public AssetFilter {
        if (kind == AssetKind.UNKNOWN) {
            // UNKNOWN has an empty wire value; it is the parser's fallback, not a kind in the
            // contract. Sending kind= would earn a 400 unrelated to anything the caller wrote.
            throw new IllegalArgumentException("UNKNOWN is this client's fallback, not a kind to filter on");
        }
        sources = sources == null ? List.of() : List.copyOf(sources);
        if (sources.contains(AssetSource.UNKNOWN)) {
            throw new IllegalArgumentException("UNKNOWN is this client's fallback, not a source to filter on");
        }
        collectionId = collectionId == null ? null : Internal.requireIdentifier(collectionId, "collectionId");
        entityId = entityId == null ? null : Internal.requireIdentifier(entityId, "entityId");
        // Only the clearly illegal side is checked. The ceiling (100 today) is the service's number,
        // and a copy of it here would be a constant that expires without saying so.
        if (limit != null && limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
    }

    /**
     * A filter that selects every ready asset in the service's default order.
     *
     * @return a filter with every field unset
     */
    public static AssetFilter all() {
        return new AssetFilter(null, null, null, null, null, null, null, null, null);
    }

    /**
     * @param value the kind to select
     * @return a copy with the kind set
     * @throws IllegalArgumentException when {@code value} is {@link AssetKind#UNKNOWN}
     */
    public AssetFilter withKind(AssetKind value) {
        return new AssetFilter(value, sources, search, favorite, collectionId, entityId, sort, cursor, limit);
    }

    /**
     * @param values the sources to select; none clears the filter
     * @return a copy with the sources set
     * @throws NullPointerException     when any value is null
     * @throws IllegalArgumentException when any value is {@link AssetSource#UNKNOWN}
     */
    public AssetFilter withSources(AssetSource... values) {
        List<AssetSource> list = Arrays.asList(Objects.requireNonNull(values, "values"));
        return new AssetFilter(kind, list, search, favorite, collectionId, entityId, sort, cursor, limit);
    }

    /**
     * @param value a case-insensitive substring of the asset name
     * @return a copy with the name search set
     */
    public AssetFilter withSearch(String value) {
        return new AssetFilter(kind, sources, value, favorite, collectionId, entityId, sort, cursor, limit);
    }

    /**
     * @param value {@code true} for favorites only, {@code false} for assets that are not
     *              favorites, {@code null} for both
     * @return a copy with the favorite filter set
     */
    public AssetFilter withFavorite(Boolean value) {
        return new AssetFilter(kind, sources, search, value, collectionId, entityId, sort, cursor, limit);
    }

    /**
     * @param value the folder (collection) id
     * @return a copy with the folder set
     * @throws IllegalArgumentException when {@code value} is blank or contains a line break
     */
    public AssetFilter withCollectionId(String value) {
        return new AssetFilter(kind, sources, search, favorite, value, entityId, sort, cursor, limit);
    }

    /**
     * @param value the character id
     * @return a copy with the character set
     * @throws IllegalArgumentException when {@code value} is blank or contains a line break
     */
    public AssetFilter withEntityId(String value) {
        return new AssetFilter(kind, sources, search, favorite, collectionId, value, sort, cursor, limit);
    }

    /**
     * @param value the order; keep it the same for every page of one walk
     * @return a copy with the sort set
     */
    public AssetFilter withSort(AssetSort value) {
        return new AssetFilter(kind, sources, search, favorite, collectionId, entityId, value, cursor, limit);
    }

    /**
     * Moves to the next page, keeping every other field as it is.
     *
     * @param value the {@link AssetPage#nextCursor()} of the page just read, unchanged
     * @return a copy pointing at the next page
     */
    public AssetFilter withCursor(String value) {
        return new AssetFilter(kind, sources, search, favorite, collectionId, entityId, sort, value, limit);
    }

    /**
     * @param value the page size, at least 1
     * @return a copy with the page size set
     * @throws IllegalArgumentException when {@code value} is below 1
     */
    public AssetFilter withLimit(int value) {
        return new AssetFilter(kind, sources, search, favorite, collectionId, entityId, sort, cursor, value);
    }

    String toQuery() {
        StringBuilder query = new StringBuilder();
        Internal.appendParameter(query, "kind", kind == null ? null : kind.wireValue());
        Internal.appendParameter(query, "source", sources.isEmpty() ? null
                : sources.stream().map(AssetSource::wireValue).collect(Collectors.joining(",")));
        Internal.appendParameter(query, "q", search);
        Internal.appendParameter(query, "favorite", favorite == null ? null : favorite.toString());
        Internal.appendParameter(query, "collectionId", collectionId);
        Internal.appendParameter(query, "entityId", entityId);
        Internal.appendParameter(query, "sort", sort == null ? null : sort.wireValue());
        Internal.appendParameter(query, "cursor", cursor);
        Internal.appendParameter(query, "limit", limit == null ? null : limit.toString());
        return query.length() == 0 ? "" : "?" + query;
    }
}
