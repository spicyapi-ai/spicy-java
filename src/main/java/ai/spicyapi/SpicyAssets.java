package ai.spicyapi;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The asset library the key reaches: the personal library for a personal key, the team's shared
 * library for a team key.
 *
 * <p>Reach it as {@link SpicyClient#assets()}; it is not constructed directly. Saved files are kept
 * until they are deleted, each with a stable {@code spicy://a/<id>} {@link Asset#uri()} that works in
 * any media field of model inputs sent with keys of the same workspace. An upload's
 * {@code spicy://f/} URI lasts one day; a saved asset does not expire.
 *
 * <p>With a team key, what may change follows the current role of the member who created the key:
 * owner and admin can save, rename, favorite and delete anything; developer can save, and rename,
 * favorite or delete what they saved; billing and readonly can only read and use assets.
 * {@link #usage()} reports it in {@link AssetUsage#scope()}.
 *
 * <p>Responses never contain a public or permanent link. {@link Asset#previewUrl()} is a private
 * preview signed for at most 20 minutes, so store {@link Asset#uri()}, never the preview.
 *
 * <p>Every method accepts an asset either as its bare id ({@code ast_...}) or as its
 * {@code spicy://a/ast_...} reference, and refuses a blank one locally, before any request. A
 * refusal from the service is a {@link SpicyApiException} whose {@link SpicyApiException#reason()}
 * names the asset library's reason when there is one; see {@link AssetErrorReason}.
 *
 * <p>No call here is billed per request: storage unlocks with the owner's lifetime top-ups, and
 * {@link #usage()} reports the plan.
 *
 * <p>Instances are immutable and safe to share, like the client that owns them.
 */
public final class SpicyAssets {

    // The longest name the service accepts, in characters. Used only to cut the default name taken
    // from a file name, never to refuse a name the caller chose: that check is the service's.
    private static final int DEFAULT_NAME_MAX_CODE_POINTS = 80;

    private final SpicyClient client;
    private final SpicyAssetCharacters characters;

    SpicyAssets(SpicyClient client) {
        this.client = client;
        this.characters = new SpicyAssetCharacters(client);
    }

    /**
     * Read-only access to the library's characters.
     *
     * @return the character namespace of this library
     */
    public SpicyAssetCharacters characters() {
        return characters;
    }

    /**
     * Saves a file to the library under a freshly generated {@code Idempotency-Key}.
     *
     * <p>The service requires the key on every save. Generating one per call also makes this
     * call's own transport retries safe: they all carry the same key, so a save whose response was
     * lost is answered from the first attempt instead of being stored twice. Saving the same source
     * again returns the asset you already have and uses no extra space.
     *
     * @param request what to save, and from where
     * @return the saved, or already saved, asset
     * @throws NullPointerException    when {@code request} is null
     * @throws SpicyApiException       when the service refuses the save; {@link SpicyApiException#reason()}
     *                                 says why for plan limits ({@code asset_library_locked},
     *                                 {@code asset_library_full}, {@code asset_file_too_large}), the
     *                                 daily limit ({@code asset_daily_limit}, with
     *                                 {@code Retry-After}), full platform storage
     *                                 ({@code asset_storage_full}) and team roles
     *                                 ({@code asset_permission_denied},
     *                                 {@code asset_workspace_forbidden})
     * @throws SpicyProtocolException  when the response is not the documented envelope
     * @throws SpicyTransportException when no usable response is obtained
     */
    public Asset create(CreateAssetRequest request) {
        return create(request, null);
    }

    /**
     * Saves a file to the library under a caller-chosen {@code Idempotency-Key}.
     *
     * <p>Pass your own key to replay one save safely after an uncertain response, for example
     * across a process restart. The service binds the key to the exact request for 24 hours.
     *
     * @param request        what to save, and from where
     * @param idempotencyKey at most 128 characters; {@code null} generates a fresh one, while a
     *                       blank key is refused rather than silently replaced
     * @return the saved, or already saved, asset
     * @throws NullPointerException     when {@code request} is null
     * @throws IllegalArgumentException when the key is blank, contains a line break or exceeds 128
     *                                  characters
     * @throws SpicyApiException        when the service refuses the save; see
     *                                  {@link #create(CreateAssetRequest)}
     * @throws SpicyProtocolException   when the response is not the documented envelope
     * @throws SpicyTransportException  when no usable response is obtained
     */
    public Asset create(CreateAssetRequest request, String idempotencyKey) {
        Objects.requireNonNull(request, "request");
        String key = idempotencyKey == null
                ? UUID.randomUUID().toString()
                : SpicyClient.requireIdempotencyKey(idempotencyKey);
        return client.call("POST", "/assets", request, Map.of("Idempotency-Key", key), true,
                client.requestTimeout(), Asset.class);
    }

    /**
     * Lists the first page of ready assets, newest first.
     *
     * @return the first page
     * @throws SpicyApiException       when the service rejects the request
     * @throws SpicyProtocolException  when the response is not the documented envelope
     * @throws SpicyTransportException when no usable response is obtained
     */
    public AssetPage list() {
        return list(AssetFilter.all());
    }

    /**
     * Lists one page of ready assets.
     *
     * <p>Continue with {@code filter.withCursor(page.nextCursor())}, keeping every other field,
     * the sort included, unchanged; or use {@link #listAll(AssetFilter)}. A cursor sent with a
     * different sort than the one it came from is refused with HTTP 400, which is surfaced as it is
     * and not retried: resending it cannot succeed.
     *
     * @param filter the filter and order to apply; use {@link AssetFilter#all()} for none
     * @return the matching page
     * @throws NullPointerException    when {@code filter} is null
     * @throws SpicyApiException       when the service rejects the request: 400 for a cursor from
     *                                 another sort, 404 for a folder outside this library, 403 with
     *                                 {@code asset_workspace_forbidden} for a key that cannot reach
     *                                 the library
     * @throws SpicyProtocolException  when the response is not the documented envelope
     * @throws SpicyTransportException when no usable response is obtained
     */
    public AssetPage list(AssetFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return client.call("GET", "/assets" + filter.toQuery(), null, Map.of(), true, client.requestTimeout(),
                AssetPage.class);
    }

    /**
     * Every ready asset, newest first, page by page.
     *
     * @return a lazy iterable; see {@link #listAll(AssetFilter)}
     */
    public Iterable<Asset> listAll() {
        return listAll(AssetFilter.all());
    }

    /**
     * Every matching asset, page by page, following {@code nextCursor} until the last page.
     *
     * <p>Lazy: a page is requested only when iteration reaches it, so breaking out of the loop
     * stops the fetching, and each new iteration starts again from the filter's own cursor. The
     * filter, the sort included, is sent unchanged on every page; only the cursor moves. The
     * filter's {@code limit} sets the page size, not the total.
     *
     * <p>Failures surface from the iterator's {@code hasNext()} as the same exceptions
     * {@link #list(AssetFilter)} throws. A service that hands back a cursor it already returned
     * would loop forever, so that ends the walk with a {@link SpicyProtocolException}.
     *
     * @param filter the filter and order to apply on every page
     * @return a lazy iterable over every matching asset
     * @throws NullPointerException when {@code filter} is null
     */
    public Iterable<Asset> listAll(AssetFilter filter) {
        Objects.requireNonNull(filter, "filter");
        return new CursorWalk<>(filter.cursor(), cursor -> {
            AssetPage page = list(filter.withCursor(cursor));
            return new CursorWalk.Page<>(page.items(), page.nextCursor());
        }, "asset listing");
    }

    /**
     * Fetches one asset with a fresh private preview.
     *
     * @param assetId {@code ast_...} or {@code spicy://a/ast_...}
     * @return the asset
     * @throws IllegalArgumentException when {@code assetId} is blank or an empty reference
     * @throws SpicyApiException        with status 404 when the asset is unknown, belongs to
     *                                  another account or sits in a library this key does not
     *                                  reach; the cases are deliberately indistinguishable
     * @throws SpicyProtocolException   when the response is not the documented envelope
     * @throws SpicyTransportException  when no usable response is obtained
     */
    public Asset get(String assetId) {
        return client.call("GET", assetPath(assetId), null, Map.of(), true, client.requestTimeout(), Asset.class);
    }

    /**
     * Renames an asset, marks or unmarks it as a favorite, or both.
     *
     * <p>Only the fields given are sent, so a favorite toggle leaves the name alone. The
     * {@code uri} stays the same and nothing is copied. Favorites belong to the library, so
     * everyone in a team sees the same ones. Repeating the request is safe, so it carries no
     * {@code Idempotency-Key} and is retried automatically.
     *
     * @param assetId  {@code ast_...} or {@code spicy://a/ast_...}
     * @param name     the new name, 1 to 80 characters; {@code null} leaves the name unchanged
     * @param favorite the new favorite mark; {@code null} leaves it unchanged
     * @return the updated asset
     * @throws IllegalArgumentException when {@code assetId} is blank, or when both {@code name} and
     *                                  {@code favorite} are null: the service would answer that with
     *                                  a misleading "invalid name", so it is refused before sending
     * @throws SpicyApiException        with status 404 for an asset this key cannot see, or 403 with
     *                                  {@code asset_permission_denied} when the team role does not
     *                                  allow the change
     * @throws SpicyProtocolException   when the response is not the documented envelope
     * @throws SpicyTransportException  when no usable response is obtained
     */
    public Asset update(String assetId, String name, Boolean favorite) {
        String path = assetPath(assetId);
        if (name == null && favorite == null) {
            throw new IllegalArgumentException("update needs a name, a favorite flag, or both");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        if (name != null) {
            body.put("name", name);
        }
        if (favorite != null) {
            body.put("favorite", favorite);
        }
        return client.call("PATCH", path, body, Map.of(), true, client.requestTimeout(), Asset.class);
    }

    /**
     * Deletes an asset.
     *
     * <p>It disappears at once, is removed from every folder and character, and its {@code uri}
     * stops working in new requests. Deleting an unknown or already deleted asset returns the same
     * result, so the call is safe to repeat and is retried automatically. Deleting does not give the
     * day's allowance of new assets back.
     *
     * @param assetId {@code ast_...} or {@code spicy://a/ast_...}
     * @return the deletion result
     * @throws IllegalArgumentException when {@code assetId} is blank or an empty reference
     * @throws SpicyApiException        with status 403 and {@code asset_permission_denied} when the
     *                                  team role does not allow the deletion
     * @throws SpicyProtocolException   when the response is not the documented envelope
     * @throws SpicyTransportException  when no usable response is obtained
     */
    public AssetDeleted delete(String assetId) {
        return client.call("DELETE", assetPath(assetId), null, Map.of(), true, client.requestTimeout(),
                AssetDeleted.class);
    }

    /**
     * Reads the storage plan, what is stored, the limits and how many assets were saved today.
     *
     * @return the allowance behind this key's library, with {@link AssetUsage#scope()} saying which
     *         library the key reaches and what it may do there
     * @throws SpicyApiException       when the service rejects the request
     * @throws SpicyProtocolException  when the response is not the documented envelope
     * @throws SpicyTransportException when no usable response is obtained
     */
    public AssetUsage usage() {
        return client.call("GET", "/assets/usage", null, Map.of(), true, client.requestTimeout(), AssetUsage.class);
    }

    /**
     * Uploads a local file and saves it to the library, named after the file.
     *
     * @param path the file to upload; its media type is inferred from the extension
     * @return the saved asset
     * @throws IOException see {@link #uploadFile(Path, UploadContentType, String)}
     */
    public Asset uploadFile(Path path) throws IOException {
        return uploadFile(path, null, null);
    }

    /**
     * Uploads a local file and saves it to the library under a chosen name.
     *
     * @param path the file to upload; its media type is inferred from the extension
     * @param name the asset name; {@code null} uses the file name
     * @return the saved asset
     * @throws IOException see {@link #uploadFile(Path, UploadContentType, String)}
     */
    public Asset uploadFile(Path path, String name) throws IOException {
        return uploadFile(path, null, name);
    }

    /**
     * Uploads a local file and saves it to the library in one call.
     *
     * <p>The upload is {@link SpicyClient#uploadFile(Path, UploadContentType)}, so its media types
     * and size limits apply; the save is {@link #create(CreateAssetRequest)} from that upload. Every
     * call uploads again, so there is no idempotency key to pass: the save uses a fresh one for its
     * own retries. When the upload fails nothing is saved.
     *
     * @param path        the file to upload
     * @param contentType its media type; {@code null} infers it from the extension
     * @param name        the asset name; {@code null} uses the file name, cut to 80 characters
     * @return the saved asset
     * @throws NullPointerException     when {@code path} is null
     * @throws IllegalArgumentException when the type cannot be inferred, or the file is empty or
     *                                  above the type's ceiling
     * @throws IOException              when the file cannot be read
     * @throws SpicyUploadException     when storage refuses the transfer
     * @throws SpicyApiException        when the ticket, the commit or the save is refused
     * @throws SpicyTransportException  when no usable response is obtained
     */
    public Asset uploadFile(Path path, UploadContentType contentType, String name) throws IOException {
        Objects.requireNonNull(path, "path");
        UploadedFile uploaded = contentType == null ? client.uploadFile(path) : client.uploadFile(path, contentType);
        String chosen = name != null ? name : defaultName(path);
        return create(CreateAssetRequest.fromUpload(uploaded.fileId()).withName(chosen));
    }

    // Cut on code points rather than chars, so a default name never ends in half a surrogate pair.
    private static String defaultName(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return null;
        }
        String name = fileName.toString().trim();
        if (name.isEmpty()) {
            return null;
        }
        StringBuilder cut = new StringBuilder();
        name.codePoints().limit(DEFAULT_NAME_MAX_CODE_POINTS).forEach(cut::appendCodePoint);
        return cut.toString();
    }

    private static String assetPath(String assetId) {
        return "/assets/" + Internal.encodePathSegment(Internal.bareAssetId(assetId, "assetId"));
    }
}
