package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Optional;

/**
 * A file saved in the asset library.
 *
 * <p><b>Store {@link #uri()}, never {@link #previewUrl()}.</b> The {@code uri} is the stable
 * reference that goes into any media field of a model input sent with keys of the same workspace,
 * and it keeps working until the asset is deleted. The preview is a private link signed for at
 * most 20 minutes; nothing in the API returns a public or permanent link.
 *
 * @param id                  the asset identifier, {@code ast_...}
 * @param uri                 the stable reference, {@code spicy://a/ast_...}; it is never a
 *                            downloadable link
 * @param name                up to 80 characters
 * @param kind                image, video or audio
 * @param contentType         the media type the service measured
 * @param bytes               the stored size
 * @param width               pixel width, for images and video
 * @param height              pixel height, for images and video
 * @param durationSeconds     measured duration, for audio and video
 * @param source              where the file came from
 * @param sourceId            the task or upload it came from; for a link, an opaque digest of the
 *                            address, which itself is not stored
 * @param createdAt           when the asset was saved
 * @param updatedAt           when the asset last changed
 * @param previewUrl          a private preview signed for at most 20 minutes; empty when signing
 *                            failed. Show it, do not store or share it
 * @param previewUrlExpiresAt when the preview stops working
 * @param favorite            the library's favorite mark, shared by everyone in a team
 * @param model               the public name of the model that generated the asset; empty for
 *                            uploads and links
 * @param modelName           that model's display name; empty when the asset was not generated or
 *                            the name is unknown
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Asset(String id, String uri, String name, AssetKind kind, String contentType, long bytes,
                    Integer width, Integer height, Double durationSeconds, AssetSource source, String sourceId,
                    Instant createdAt, Instant updatedAt, String previewUrl, Instant previewUrlExpiresAt,
                    boolean favorite, String model, String modelName) {

    /**
     * The private preview, when the service could sign one.
     *
     * @return the preview link, or empty when signing failed
     */
    public Optional<String> preview() {
        return previewUrl == null || previewUrl.isEmpty() ? Optional.empty() : Optional.of(previewUrl);
    }

    /**
     * The model that generated this asset, when one did.
     *
     * @return the model's public name, or empty for uploads and links
     */
    public Optional<String> generatedBy() {
        return model == null || model.isEmpty() ? Optional.empty() : Optional.of(model);
    }
}
