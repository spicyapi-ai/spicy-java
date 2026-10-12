package ai.spicyapi;

import java.util.Map;
import java.util.Objects;

/**
 * The characters of the library the key reaches, read-only.
 *
 * <p>Reach it as {@code client.assets().characters()}; it is not constructed directly. A character
 * is a named, ordered set of images arranged on the Assets page of the web app. It references
 * assets instead of copying them, so each image carries its own {@code spicy://a/<id>}
 * {@link Asset#uri()}. To use a character in a generation request, put
 * {@link AssetCharacter#uris()}, in order, into the model's reference image field.
 *
 * <p>Personas became characters in the personal library on 2026-10-12. Existing
 * {@code spicy://p/<id>} references keep resolving to the same images; new code should use the
 * {@code spicy://a/} references listed here.
 *
 * <p>Reading characters shares the limit of 60 requests per minute per account with reading
 * assets. Free.
 */
public final class SpicyAssetCharacters {

    private final SpicyClient client;

    SpicyAssetCharacters(SpicyClient client) {
        this.client = client;
    }

    /**
     * Lists the first page of characters, newest first.
     *
     * @return the first page
     * @throws SpicyApiException       when the service rejects the request
     * @throws SpicyProtocolException  when the response is not the documented envelope
     * @throws SpicyTransportException when no usable response is obtained
     */
    public AssetCharacterPage list() {
        return list(AssetCharacterFilter.all());
    }

    /**
     * Lists one page of characters, newest first.
     *
     * <p>Continue with {@code filter.withCursor(page.nextCursor())}, or use
     * {@link #listAll(AssetCharacterFilter)}.
     *
     * @param filter the filter to apply; use {@link AssetCharacterFilter#all()} for none
     * @return the matching page
     * @throws NullPointerException    when {@code filter} is null
     * @throws SpicyApiException       when the service rejects the request
     * @throws SpicyProtocolException  when the response is not the documented envelope
     * @throws SpicyTransportException when no usable response is obtained
     */
    public AssetCharacterPage list(AssetCharacterFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return client.call("GET", "/assets/entities" + filter.toQuery(), null, Map.of(), true,
                client.requestTimeout(), AssetCharacterPage.class);
    }

    /**
     * Every character, page by page.
     *
     * @return a lazy iterable; see {@link #listAll(AssetCharacterFilter)}
     */
    public Iterable<AssetCharacter> listAll() {
        return listAll(AssetCharacterFilter.all());
    }

    /**
     * Every matching character, page by page, following {@code nextCursor} until the last page.
     *
     * <p>Lazy, exactly like {@link SpicyAssets#listAll(AssetFilter)}: a page is requested only when
     * iteration reaches it, the filter is repeated on every page, and its {@code limit} sets the
     * page size, not the total. A cursor that comes back twice ends the walk with a
     * {@link SpicyProtocolException}.
     *
     * @param filter the filter to apply on every page
     * @return a lazy iterable over every matching character
     * @throws NullPointerException when {@code filter} is null
     */
    public Iterable<AssetCharacter> listAll(AssetCharacterFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return new CursorWalk<>(filter.cursor(), cursor -> {
            AssetCharacterPage page = list(filter.withCursor(cursor));
            return new CursorWalk.Page<>(page.items(), page.nextCursor());
        }, "character listing");
    }

    /**
     * Fetches one character with its images in order, each with a fresh private preview.
     *
     * @param characterId the character identifier
     * @return the character
     * @throws IllegalArgumentException when {@code characterId} is blank
     * @throws SpicyApiException        with status 404 when the character is unknown, belongs to
     *                                  another account or sits in a library this key does not
     *                                  reach; the cases are deliberately indistinguishable
     * @throws SpicyProtocolException   when the response is not the documented envelope
     * @throws SpicyTransportException  when no usable response is obtained
     */
    public AssetCharacter get(String characterId) {
        String path = "/assets/entities/"
                + Internal.encodePathSegment(Internal.requireIdentifier(characterId, "characterId"));
        return client.call("GET", path, null, Map.of(), true, client.requestTimeout(), AssetCharacter.class);
    }
}
