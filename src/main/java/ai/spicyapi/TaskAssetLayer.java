package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Where one asset of a layer decomposition sits in the picture.
 *
 * <p>The first asset of such a result is the base image, the source with the separated elements
 * removed, at {@code zIndex} 0; every other asset is one transparent PNG layer. Stack them by
 * ascending {@code zIndex} to rebuild the picture.
 *
 * @param zIndex      stacking order, 0 for the base image
 * @param name        the model's label for the layer, when it gives one
 * @param boundingBox {@code [x1, y1, x2, y2]} in base-image pixels: scale the layer to this box to
 *                    place it on the base. Empty on the base image and on models that do not report
 *                    positions, in which case the layer is still usable as a standalone cut-out
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskAssetLayer(@JsonProperty("zIndex") int zIndex, String name, List<Integer> boundingBox) {

    // An absent box becomes an empty list, so callers never null-check it.
    public TaskAssetLayer {
        boundingBox = Internal.emptyIfNull(boundingBox);
    }

    /**
     * Whether the model reported where this layer sits on the base image.
     *
     * @return true when {@link #boundingBox()} holds the four corner coordinates
     */
    public boolean positioned() {
        return boundingBox.size() == 4;
    }
}
