package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The result of a finished task.
 *
 * @param text       the answer when the endpoint answers in text. Not every generation produces a
 *                   file: transcription and similar endpoints put the entire result here and return
 *                   no assets at all. For a multichannel transcription it holds one line per
 *                   channel, in channel order
 * @param assets     generated artifacts; empty for a text-only result
 * @param transcript the structured transcript, with timing and language, on speech-to-text
 *                   endpoints that report it; {@code null} everywhere else
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskOutput(String text, List<TaskAsset> assets, TaskTranscript transcript) {

    // Text-only outputs carry no assets field; normalise it to an empty list.
    public TaskOutput {
        assets = Internal.emptyIfNull(assets);
    }

    /**
     * The shape this record had before {@code transcript} was added, kept so existing callers
     * still compile.
     *
     * @param text   the answer when the endpoint answers in text
     * @param assets generated artifacts
     */
    public TaskOutput(String text, List<TaskAsset> assets) {
        this(text, assets, null);
    }
}
