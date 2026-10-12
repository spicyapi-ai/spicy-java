package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Objects;

/**
 * What to save into the asset library, and from where. Build one with a factory method rather than
 * the canonical constructor; each factory fills exactly the fields its source needs.
 *
 * <ul>
 *   <li>{@link #fromTaskOutput(String, int)} or {@link #fromTaskOutput(String, String)}: one output
 *       of one of your own successful tasks (with a team key, one of the team's), picked by its
 *       zero-based position in {@link TaskRecord#assets()} or by its {@link TaskAsset#key()}.</li>
 *   <li>{@link #fromUpload(String)}: a committed upload, as {@code fil_...} or
 *       {@code spicy://f/fil_...}.</li>
 *   <li>{@link #fromUrl(String)}: a public {@code http} or {@code https} address on port 80 or 443,
 *       without credentials. The service downloads it once and reads the type from the file
 *       itself.</li>
 * </ul>
 *
 * <p>Saving the same source again returns the asset you already have and uses no extra space.
 *
 * @param source the source kind, never {@link AssetSource#UNKNOWN}
 * @param taskId for {@link AssetSource#TASK_OUTPUT}: the task whose output to save
 * @param key    for {@link AssetSource#TASK_OUTPUT}: the output key; send this or {@code index}
 * @param index  for {@link AssetSource#TASK_OUTPUT}: the zero-based output position; send this or
 *               {@code key}
 * @param fileId for {@link AssetSource#UPLOAD}: the committed upload
 * @param url    for {@link AssetSource#URL}: the public address to download
 * @param name   optional display name, up to 80 characters; the service defaults it to
 *               {@code Saved asset}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateAssetRequest(AssetSource source, String taskId, String key, Integer index, String fileId,
                                 String url, String name) {

    public CreateAssetRequest {
        Objects.requireNonNull(source, "source");
        if (source == AssetSource.UNKNOWN) {
            throw new IllegalArgumentException("UNKNOWN is this client's fallback, not a source to save from");
        }
        taskId = taskId == null ? null : Internal.requireIdentifier(taskId, "taskId");
        fileId = fileId == null ? null : Internal.requireIdentifier(fileId, "fileId");
        url = url == null ? null : Internal.requireText(url, "url");
        if (index != null && index < 0) {
            throw new IllegalArgumentException("index must not be negative");
        }
        // Each source needs its own field. Checking only that much is deliberate: what else the
        // service accepts alongside it is the service's rule to enforce.
        switch (source) {
            case TASK_OUTPUT -> Internal.requireText(taskId, "taskId");
            case UPLOAD -> Internal.requireText(fileId, "fileId");
            case URL -> Internal.requireText(url, "url");
            default -> throw new IllegalArgumentException("unsupported source " + source);
        }
    }

    /**
     * One output of a successful task, picked by position.
     *
     * @param taskId the task that produced the output
     * @param index  zero-based position in the task's output assets
     * @return the request
     * @throws IllegalArgumentException when {@code taskId} is blank or {@code index} is negative
     */
    public static CreateAssetRequest fromTaskOutput(String taskId, int index) {
        return new CreateAssetRequest(AssetSource.TASK_OUTPUT, taskId, null, index, null, null, null);
    }

    /**
     * One output of a successful task, picked by its key.
     *
     * @param taskId the task that produced the output
     * @param key    the output's {@link TaskAsset#key()}
     * @return the request
     * @throws IllegalArgumentException when either argument is blank
     */
    public static CreateAssetRequest fromTaskOutput(String taskId, String key) {
        return new CreateAssetRequest(AssetSource.TASK_OUTPUT, taskId, Internal.requireText(key, "key"), null,
                null, null, null);
    }

    /**
     * A committed upload.
     *
     * @param fileId {@code fil_...} or {@code spicy://f/fil_...}, for example
     *               {@link UploadedFile#fileId()}
     * @return the request
     * @throws IllegalArgumentException when {@code fileId} is blank
     */
    public static CreateAssetRequest fromUpload(String fileId) {
        return new CreateAssetRequest(AssetSource.UPLOAD, null, null, null, fileId, null, null);
    }

    /**
     * A public link, downloaded once by the service.
     *
     * @param url a public {@code http} or {@code https} address on port 80 or 443, without
     *            credentials. Links to SpicyAPI storage are refused; save those with
     *            {@link #fromTaskOutput(String, int)} or {@link #fromUpload(String)} instead
     * @return the request
     * @throws IllegalArgumentException when {@code url} is blank
     */
    public static CreateAssetRequest fromUrl(String url) {
        return new CreateAssetRequest(AssetSource.URL, null, null, null, null, url, null);
    }

    /**
     * Sets the display name.
     *
     * @param value up to 80 characters; {@code null} leaves the service default
     * @return a copy with the name set
     */
    public CreateAssetRequest withName(String value) {
        return new CreateAssetRequest(source, taskId, key, index, fileId, url, value);
    }
}
