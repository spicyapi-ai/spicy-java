package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The structured result of a speech-to-text task, present only on endpoints that report it.
 *
 * <p>{@link TaskOutput#text()} stays the plain transcript; this adds timing and language details.
 * It is erased together with the rest of the task output on the prompt-retention schedule.
 *
 * @param text                the full transcript
 * @param language            the detected language as the model reports it, for example
 *                            {@code en}
 * @param languageCode        the detected language as an ISO 639-3 code, for example {@code eng},
 *                            when available
 * @param languageProbability confidence in the detected language, 0 to 1, when available
 * @param durationSeconds     length of the transcribed audio
 * @param words               timed words; empty when the model reports none
 * @param channels            one entry per audio channel when multichannel transcription was
 *                            requested; empty otherwise
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskTranscript(String text, String language, String languageCode, Double languageProbability,
                             Double durationSeconds, List<TaskTranscriptWord> words,
                             List<TaskTranscriptChannel> channels) {

    // Absent arrays become empty lists, so callers never null-check them.
    public TaskTranscript {
        words = Internal.emptyIfNull(words);
        channels = Internal.emptyIfNull(channels);
    }
}
