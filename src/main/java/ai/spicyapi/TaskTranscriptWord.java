package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One timed segment of a transcript.
 *
 * @param text      the segment text
 * @param start     start time, in seconds from the beginning of the audio
 * @param end       end time, in seconds from the beginning of the audio
 * @param type      the segment type, for example {@code word}, when the model reports one
 * @param speakerId the speaker label; present only when the model separates speakers
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskTranscriptWord(String text, double start, double end, String type, String speakerId) {
}
