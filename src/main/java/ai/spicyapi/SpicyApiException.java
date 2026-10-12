package ai.spicyapi;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

/**
 * The service answered, and the answer was a failure.
 *
 * <p>Three identifiers travel together and they are not interchangeable. {@link #httpStatus()}
 * is the transport verdict, {@link #businessCode()} is the platform's own code from the
 * response envelope, and {@link #requestId()} is what support needs in order to find the
 * request. HTTP 503 alone carries three different business codes, so any client that branches
 * on status cannot tell "a dependency blinked" from "this model has no usable deployment right
 * now" from "generation failed and your money is already back".
 */
public final class SpicyApiException extends SpicyException {

    private static final long serialVersionUID = 1L;

    // HTTP statuses the transport may retry automatically. Retrying anything else just reproduces
    // the same failure. The rule lives in this class because transientFailure() is where it belongs
    // semantically; SpicyClient reads the same set when it retries.
    static final Set<Integer> RETRYABLE_HTTP_STATUS = Set.of(408, 429, 500, 502, 503, 504);

    // Retryable business codes. 50301 means "no deployment or valid price right now", a transient
    // condition that may resolve itself.
    static final Set<Integer> RETRYABLE_BUSINESS_CODES = Set.of(429, 500, 503, 50301);

    // 50302 is absent from the set above and must additionally be caught here, ahead of the HTTP
    // status: it rides on a 503, so anything looking only at the status would retry it as ordinary
    // upstream unavailability. The contract is explicit - this idempotency key has already recorded
    // the failure, reusing it only replays that failure, and a retry requires a fresh key.
    //
    // Four automatic attempts do not charge twice, but all four are doomed, and the final error
    // reads as "we retried and it still failed", burying the actual remedy: send again under a new
    // key.
    static final Set<Integer> NON_RETRYABLE_BUSINESS_CODES = Set.of(50302);

    /**
     * @serial the transport status, or 0 when no response was received
     */
    private final int httpStatus;
    /**
     * @serial the platform business code, or 0 when the body carried none
     */
    private final int businessCode;
    /**
     * @serial the correlation identifier, never null
     */
    private final String requestId;
    private final transient Duration retryAfter;
    /**
     * @serial the failure envelope's {@code data.reason}, or null when it carried none
     */
    private final String reason;

    /**
     * @param message      the service's own explanation, already in the selected language
     * @param httpStatus   transport status, or 0 when no status was obtained
     * @param businessCode envelope {@code code}, or 0 when the body carried none
     * @param requestId    envelope {@code request_id}; {@code null} becomes empty
     * @param retryAfter   parsed {@code Retry-After}, or {@code null} when absent
     */
    public SpicyApiException(String message, int httpStatus, int businessCode, String requestId,
                             Duration retryAfter) {
        this(message, httpStatus, businessCode, requestId, retryAfter, null);
    }

    /**
     * @param message      the service's own explanation, already in the selected language
     * @param httpStatus   transport status, or 0 when no status was obtained
     * @param businessCode envelope {@code code}, or 0 when the body carried none
     * @param requestId    envelope {@code request_id}; {@code null} becomes empty
     * @param retryAfter   parsed {@code Retry-After}, or {@code null} when absent
     * @param reason       envelope {@code data.reason}, or {@code null} when absent; an empty
     *                     string is treated as absent
     */
    public SpicyApiException(String message, int httpStatus, int businessCode, String requestId,
                             Duration retryAfter, String reason) {
        super(message);
        this.httpStatus = httpStatus;
        this.businessCode = businessCode;
        this.requestId = requestId == null ? "" : requestId;
        this.retryAfter = retryAfter;
        this.reason = reason == null || reason.isEmpty() ? null : reason;
    }

    /**
     * Transport status, or 0 when no response was received.
     *
     * @return the HTTP status, or 0 when no response was received
     */
    public int httpStatus() {
        return httpStatus;
    }

    /**
     * Envelope {@code code}, or 0 when the body carried none.
     *
     * <p>This is the value to branch on. Messages are prose, they are translated, and they are
     * not part of the contract.
     *
     * @return the envelope code, or 0 when the body carried none
     */
    public int businessCode() {
        return businessCode;
    }

    /**
     * Envelope {@code request_id}, or an empty string. Quote it in support requests.
     *
     * @return the correlation identifier, or an empty string
     */
    public String requestId() {
        return requestId;
    }

    /**
     * {@code Retry-After} as sent by the service, when it sent one.
     *
     * @return the delay the service asked for, when it asked
     */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    /**
     * The failure envelope's {@code data.reason}, when the service sent one.
     *
     * <p>Branch on this, alongside {@link #businessCode()}, rather than on the message. Today the
     * asset library is what sends reasons, and {@link AssetErrorReason} lists the values in use; any
     * other string the service sends is carried here unchanged, so keep a default branch and handle
     * an unknown value like an absent one. {@code code}, the HTTP status and the message are the
     * same with or without a reason.
     *
     * @return the raw reason, or empty when the failure carried none
     */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    /**
     * {@link #reason()} mapped onto the asset library's known reasons.
     *
     * @return the known reason, or empty when there is no reason or it is one this release does
     *         not know (the raw value is still in {@link #reason()})
     */
    public Optional<AssetErrorReason> assetErrorReason() {
        return AssetErrorReason.fromWireValue(reason);
    }

    /**
     * Whether resending the identical request could plausibly succeed.
     *
     * <p>This answers "is the failure transient", which is not the same question as "is it safe
     * to resend". For task creation the second question is decided by the
     * {@code Idempotency-Key}, which is why {@link SpicyClient#createTask(CreateTaskRequest, String)} only enables
     * automatic retries when a key was supplied.
     *
     * @return true when an identical request could plausibly succeed later
     */
    public boolean transientFailure() {
        if (NON_RETRYABLE_BUSINESS_CODES.contains(businessCode)) {
            return false;
        }
        return RETRYABLE_HTTP_STATUS.contains(httpStatus) || RETRYABLE_BUSINESS_CODES.contains(businessCode);
    }

    /**
     * What to do about this specific code, in one sentence.
     *
     * <p>Intended for logs and operator-facing messages. Programs should branch on
     * {@link #businessCode()}.
     *
     * @return one sentence describing the fix
     */
    public String remediation() {
        // A reason is more specific than the code it rides on: an asset_library_full is a 400, and
        // the generic advice for a 400 or a 403 would point at the wrong fix.
        Optional<AssetErrorReason> known = assetErrorReason();
        if (known.isPresent()) {
            return switch (known.get()) {
                case ASSET_LIBRARY_LOCKED -> "The asset library opens after the account owner's first top-up. Top up, "
                        + "then save again; assets().usage() shows the plans.";
                case ASSET_LIBRARY_FULL -> "The library's storage or item allowance is used up. Delete assets you no "
                        + "longer need, or unlock the next plan with a top-up; assets().usage() shows the numbers.";
                case ASSET_FILE_TOO_LARGE -> "The file is above the plan's per-file limit. Save a smaller file, or "
                        + "unlock a plan with a higher limit; assets().usage() shows it.";
                case ASSET_DAILY_LIMIT -> "Today's allowance of new assets is used up; it resets at 00:00 UTC. Wait "
                        + "for Retry-After, then save again. Deleting assets does not give the allowance back.";
                case ASSET_STORAGE_FULL -> "Storage is temporarily full across the platform. Retry later.";
                case ASSET_PERMISSION_DENIED -> "The team role of the member who created this key does not allow "
                        + "this change. Ask a team owner or admin, or use a key whose creator may make it.";
                case ASSET_WORKSPACE_FORBIDDEN -> "This key cannot reach that library, for example because its "
                        + "creator left the team. Use a key created in the workspace that owns the library.";
                case ASSET_ENTITY_NAME_TAKEN -> "The library already has a character with that name, ignoring case. "
                        + "Choose another name.";
            };
        }
        return switch (businessCode) {
            case 40003 -> "The stored bytes, media type or file signature do not match the upload ticket. "
                    + "Request a fresh ticket and upload the file again; repeating the commit cannot fix it.";
            case 40004 -> "The request is valid but no deployment serves this exact parameter combination. "
                    + "Change the parameter named in the message and submit again; an unchanged retry fails "
                    + "identically.";
            case 40201 -> "Insufficient balance. Add funds, then submit again.";
            case 40202 -> "A spend cap was reached. Raise the cap or wait for the next window.";
            case 40310 -> "The account has not verified its email address, so it cannot run models yet. Open "
                    + "the verification link sent at sign-up, or send a new one from the Console; the same "
                    + "key starts working once the address is verified.";
            case 40901 -> "The quote expired or the request changed after it was quoted. Quote again and send "
                    + "the new quoteId and expectedCost.";
            case 50301 -> "This model has no usable deployment or effective price at the moment. Back off and "
                    + "retry, or select another model.";
            case 50302 -> "A synchronous generation failed upstream and the charge was already refunded. Retry "
                    + "under a NEW Idempotency-Key: the original key replays the recorded failure.";
            case 503 -> "A dependency is temporarily unavailable. Honour Retry-After, then retry.";
            default -> switch (httpStatus) {
                case 401 -> "The API key is missing, malformed, expired or revoked.";
                case 403 -> "Account, model, key, IP or region authorization failed.";
                case 404 -> "No such resource for this account and API key; the two cases are deliberately "
                        + "indistinguishable.";
                case 409 -> "This Idempotency-Key is already bound to a different request, or belongs to another "
                        + "API key. Use a fresh key for a genuinely new request.";
                case 413 -> "The request body exceeds the accepted size. Upload large media through the upload "
                        + "endpoints instead of inlining it.";
                case 429 -> "Rate limited. Wait for Retry-After, then retry.";
                default -> "Quote request_id to support if this persists.";
            };
        };
    }

    /**
     * Status, code, request ID, reason, message and remediation on a single line, for logs.
     *
     * @return a single-line summary suitable for a log record
     */
    public String describe() {
        StringBuilder out = new StringBuilder("HTTP ").append(httpStatus);
        if (businessCode != 0) {
            out.append(" code ").append(businessCode);
        }
        if (!requestId.isEmpty()) {
            out.append(" request_id=").append(requestId);
        }
        if (reason != null) {
            out.append(" reason=").append(reason);
        }
        return out.append(": ").append(getMessage()).append(" -> ").append(remediation()).toString();
    }
}
