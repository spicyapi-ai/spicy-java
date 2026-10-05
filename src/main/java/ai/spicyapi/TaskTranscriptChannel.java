package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The transcript of one audio channel, from a multichannel transcription.
 *
 * @param index        zero-based channel index
 * @param text         this channel's transcript
 * @param language     the language detected on this channel
 * @param languageCode the ISO 639-3 code of that language, when available
 * @param words        this channel's timed words; empty when the model reports none
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskTranscriptChannel(int index, String text, String language, String languageCode,
                                    List<TaskTranscriptWord> words) {

    // Absent arrays become empty lists, so callers never null-check them.
    public TaskTranscriptChannel {
        words = Internal.emptyIfNull(words);
    }
}
