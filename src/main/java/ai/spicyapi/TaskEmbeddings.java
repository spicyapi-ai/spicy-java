package ai.spicyapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Vectors produced by a text embedding task.
 * @param inputTokens measured input token count
 * @param data one vector for each input
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TaskEmbeddings(int inputTokens, List<Vector> data) {
    /** Normalizes an absent array to an empty list. */
    public TaskEmbeddings {
        data = Internal.emptyIfNull(data);
    }

    /**
     * A vector and its position in the input.
     * @param index zero-based position
     * @param embedding vector coordinates
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Vector(int index, List<Double> embedding) {
        /** Normalizes an absent array to an empty list. */
        public Vector {
            embedding = Internal.emptyIfNull(embedding);
        }
    }
}
