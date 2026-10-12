package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Media types accepted by direct upload, with the size ceiling that applies to each.
 *
 * <p>A publicly reachable HTTPS URL can be placed in model input directly and needs no upload
 * at all; these types matter only for bytes held locally.
 *
 * <p>The document types (PDF, Office, iWork, plain text and Markdown) are reference files for the
 * models whose schema asks for one. The service checks only that the bytes belong to the declared
 * family; it does not read the content.
 *
 * <p>{@link #APPLICATION_OCTET_STREAM} carries LoRA weights: a {@code .safetensors} file, for a
 * model input's {@code loras[].path}.
 */
public enum UploadContentType {

    /** JPEG image, up to 10 MiB. */
    IMAGE_JPEG("image/jpeg", 10L * 1024 * 1024),
    /** PNG image, up to 10 MiB. */
    IMAGE_PNG("image/png", 10L * 1024 * 1024),
    /** WebP image, up to 10 MiB. */
    IMAGE_WEBP("image/webp", 10L * 1024 * 1024),
    /** GIF image, up to 10 MiB. */
    IMAGE_GIF("image/gif", 10L * 1024 * 1024),
    /** MP4 video, up to 90 MiB and 600 seconds. */
    VIDEO_MP4("video/mp4", 90L * 1024 * 1024),
    /** WebM video, up to 90 MiB and 600 seconds. */
    VIDEO_WEBM("video/webm", 90L * 1024 * 1024),
    /** MP3 audio, up to 90 MiB and 600 seconds. */
    AUDIO_MPEG("audio/mpeg", 90L * 1024 * 1024),
    /** WAV audio, up to 90 MiB and 600 seconds. */
    AUDIO_WAV("audio/wav", 90L * 1024 * 1024),
    /** PDF document, up to 90 MiB. */
    APPLICATION_PDF("application/pdf", 90L * 1024 * 1024),
    /** Legacy Word document ({@code .doc}), up to 90 MiB. */
    APPLICATION_MSWORD("application/msword", 90L * 1024 * 1024),
    /** Word document ({@code .docx}), up to 90 MiB. */
    APPLICATION_DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            90L * 1024 * 1024),
    /** Legacy Excel workbook ({@code .xls}), up to 90 MiB. */
    APPLICATION_MS_EXCEL("application/vnd.ms-excel", 90L * 1024 * 1024),
    /** Excel workbook ({@code .xlsx}), up to 90 MiB. */
    APPLICATION_XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            90L * 1024 * 1024),
    /** Legacy PowerPoint deck ({@code .ppt}), up to 90 MiB. */
    APPLICATION_MS_POWERPOINT("application/vnd.ms-powerpoint", 90L * 1024 * 1024),
    /** PowerPoint deck ({@code .pptx}), up to 90 MiB. */
    APPLICATION_PPTX("application/vnd.openxmlformats-officedocument.presentationml.presentation",
            90L * 1024 * 1024),
    /** Keynote deck ({@code .key}), up to 90 MiB. */
    APPLICATION_KEYNOTE("application/vnd.apple.keynote", 90L * 1024 * 1024),
    /** Pages document ({@code .pages}), up to 90 MiB. */
    APPLICATION_PAGES("application/vnd.apple.pages", 90L * 1024 * 1024),
    /** Numbers spreadsheet ({@code .numbers}), up to 90 MiB. */
    APPLICATION_NUMBERS("application/vnd.apple.numbers", 90L * 1024 * 1024),
    /** Plain UTF-8 text ({@code .txt}), up to 90 MiB. */
    TEXT_PLAIN("text/plain", 90L * 1024 * 1024),
    /** Markdown ({@code .md}), up to 90 MiB. */
    TEXT_MARKDOWN("text/markdown", 90L * 1024 * 1024),
    /**
     * LoRA weights ({@code .safetensors}), up to 2 GiB. The commit checks that the file is a
     * complete safetensors file, and its URI goes in {@code loras[].path}. The upload ticket stays
     * valid for two hours; a file this size usually needs a longer upload timeout than the default.
     */
    APPLICATION_OCTET_STREAM("application/octet-stream", 2L * 1024 * 1024 * 1024);

    private static final Map<String, UploadContentType> BY_EXTENSION = Map.ofEntries(
            Map.entry("jpg", IMAGE_JPEG),
            Map.entry("jpeg", IMAGE_JPEG),
            Map.entry("png", IMAGE_PNG),
            Map.entry("webp", IMAGE_WEBP),
            Map.entry("gif", IMAGE_GIF),
            Map.entry("mp4", VIDEO_MP4),
            Map.entry("webm", VIDEO_WEBM),
            Map.entry("mp3", AUDIO_MPEG),
            Map.entry("wav", AUDIO_WAV),
            Map.entry("pdf", APPLICATION_PDF),
            Map.entry("doc", APPLICATION_MSWORD),
            Map.entry("docx", APPLICATION_DOCX),
            Map.entry("xls", APPLICATION_MS_EXCEL),
            Map.entry("xlsx", APPLICATION_XLSX),
            Map.entry("ppt", APPLICATION_MS_POWERPOINT),
            Map.entry("pptx", APPLICATION_PPTX),
            Map.entry("key", APPLICATION_KEYNOTE),
            Map.entry("pages", APPLICATION_PAGES),
            Map.entry("numbers", APPLICATION_NUMBERS),
            Map.entry("txt", TEXT_PLAIN),
            Map.entry("md", TEXT_MARKDOWN),
            Map.entry("markdown", TEXT_MARKDOWN),
            Map.entry("safetensors", APPLICATION_OCTET_STREAM));

    private final String mediaType;
    private final long maxBytes;

    UploadContentType(String mediaType, long maxBytes) {
        this.mediaType = mediaType;
        this.maxBytes = maxBytes;
    }

    /**
     * The IANA media type sent to the service.
     *
     * @return the media type string, for example {@code image/png}
     */
    @JsonValue
    public String mediaType() {
        return mediaType;
    }

    /**
     * Whether this is one of the reference document types rather than an image, audio, video or
     * LoRA weights.
     *
     * @return true for PDF, Office, iWork, plain text and Markdown
     */
    public boolean document() {
        return !mediaType.startsWith("image/") && !mediaType.startsWith("audio/")
                && !mediaType.startsWith("video/") && this != APPLICATION_OCTET_STREAM;
    }

    /**
     * The documented ceiling for this type.
     *
     * <p>Checked locally so an oversized file fails before it is uploaded; the ticket returned
     * by the service carries the authoritative limit in {@link UploadTicket#maxBytes()}.
     *
     * @return the documented ceiling, in bytes
     */
    public long maxBytes() {
        return maxBytes;
    }

    /**
     * Resolves a media type string.
     *
     * @param mediaType for example {@code image/png}; parameters such as {@code ; charset} are
     *                  not accepted because the service matches the type exactly
     * @return the matching constant
     * @throws IllegalArgumentException when the type is not one this platform accepts
     */
    public static UploadContentType fromMediaType(String mediaType) {
        String normalized = Internal.requireText(mediaType, "mediaType").toLowerCase(Locale.ROOT);
        for (UploadContentType type : values()) {
            if (type.mediaType.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("unsupported upload media type: " + mediaType);
    }

    // The extension table, for the test that every type the contract accepts can be inferred from
    // some file name. The table is written by hand while the types follow the contract, and the
    // contract once gained twelve document types the table had never heard of.
    static Map<String, UploadContentType> byExtension() {
        return BY_EXTENSION;
    }

    /**
     * Guesses the media type from a file name extension.
     *
     * @param fileName a file name or path
     * @return the matching constant, or empty when the extension is unknown or absent
     */
    public static Optional<UploadContentType> fromFileName(String fileName) {
        if (fileName == null) {
            return Optional.empty();
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_EXTENSION.get(fileName.substring(dot + 1).toLowerCase(Locale.ROOT)));
    }
}
