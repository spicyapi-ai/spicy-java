package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * A character: a named, ordered set of images in the asset library.
 *
 * <p>A character references assets instead of copying them, so each image is an ordinary
 * {@link Asset} with its own {@code spicy://a/...} {@link Asset#uri()}. To use a character in a
 * generation request, put {@link #uris()}, in order, into the model's reference image field.
 * Characters are created and arranged on the Assets page of the web app; the API only reads them.
 *
 * <p>Characters used to be called personas. Existing {@code spicy://p/...} references keep
 * resolving to the same images, in the personal library only; new code should use the
 * {@code spicy://a/} references listed here.
 *
 * @param id           the character identifier
 * @param kind         always {@code character} today
 * @param name         unique within the library, ignoring case; up to 40 characters
 * @param note         an optional note of up to 280 characters; empty when there is none
 * @param coverAssetId the cover image's asset id; empty while the character has no images
 * @param cover        the cover image; {@code null} while the character has no images
 * @param assets       the character's images, in order
 * @param count        how many images the character has
 * @param createdAt    when the character was created
 * @param updatedAt    when the character last changed
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AssetCharacter(String id, String kind, String name, String note, String coverAssetId, Asset cover,
                             List<Asset> assets, int count, Instant createdAt, Instant updatedAt) {

    public AssetCharacter {
        assets = Internal.emptyIfNull(assets);
    }

    /**
     * The cover image, when the character has one.
     *
     * @return the cover, or empty while the character has no images
     */
    public Optional<Asset> coverImage() {
        return Optional.ofNullable(cover);
    }

    /**
     * The stable references of the character's images, in order: what goes into a model's
     * reference image field.
     *
     * @return the {@code spicy://a/...} references, in the character's order
     */
    public List<String> uris() {
        return assets.stream().map(Asset::uri).toList();
    }
}
