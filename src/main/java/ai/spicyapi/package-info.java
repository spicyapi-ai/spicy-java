/**
 * Java client for the SpicyAPI generation platform.
 *
 * <p>Start at {@link ai.spicyapi.SpicyClient}. The asynchronous media task is the main subject:
 * catalogue lookup, price quote, submission, polling, retry and content destruction, plus direct
 * media upload, download URLs and callback signature verification. Files used again and again live
 * in the asset library, {@link ai.spicyapi.SpicyClient#assets()}, which also reads the library's
 * characters.
 *
 * <p>Text models are not covered here. They are served through the OpenAI, Anthropic and Gemini
 * compatible layers, where the official vendor SDKs work by changing only the base URL.
 */
package ai.spicyapi;
