package ai.spicyapi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The asset library and its characters: what goes on the wire for every method and parameter, and
 * the negative case next to each one - the value that must not be sent, the request that must not
 * leave the process, the failure that must not be retried.
 */
class AssetLibraryTest {

    private static final ObjectMapper MAPPER = SpicyClient.newObjectMapper();
    private static final Pattern UUID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private static String asset(String id) {
        return asset(id, "Hero portrait", false);
    }

    private static String asset(String id, String name, boolean favorite) {
        return """
                {"id":"%1$s","uri":"spicy://a/%1$s","name":"%2$s","kind":"image","contentType":"image/png",
                 "bytes":1843201,"width":1536,"height":1024,"source":"task_output","sourceId":"job_1",
                 "createdAt":"2026-10-11T08:20:11Z","updatedAt":"2026-10-11T08:20:11Z",
                 "previewUrl":"https://preview.example.test/private","previewUrlExpiresAt":"2026-10-11T08:40:11Z",
                 "favorite":%3$s,"model":"publisher/model-5.0/text-to-image","modelName":"Model 5.0"}"""
                .formatted(id, name, favorite);
    }

    private static String page(String nextCursor, String... ids) {
        List<String> items = new ArrayList<>();
        for (String id : ids) {
            items.add(asset(id));
        }
        return "{\"items\":[" + String.join(",", items) + "]"
                + (nextCursor == null ? "" : ",\"nextCursor\":\"" + nextCursor + "\"") + "}";
    }

    private static String character(String id) {
        return """
                {"id":"%s","kind":"character","name":"Mia","note":"","coverAssetId":"ast_front",
                 "cover":%s,"assets":[%s,%s],"count":2,
                 "createdAt":"2026-10-12T08:00:00Z","updatedAt":"2026-10-12T08:00:00Z"}"""
                .formatted(id, asset("ast_front"), asset("ast_front"), asset("ast_side"));
    }

    private static String failure(int status, String extra) {
        return "{\"code\":" + status + ",\"msg\":\"refused by the asset library\",\"request_id\":\"req_fail\""
                + (extra == null ? "" : "," + extra) + "}";
    }

    /** One scripted reply. */
    private record Reply(int status, String body, Map<String, String> headers) {
        static Reply ok(String data) {
            return new Reply(200, StubServer.envelope(data), Map.of());
        }

        static Reply fail(int status, String extra) {
            return new Reply(status, failure(status, extra), Map.of());
        }
    }

    /** Answers the asset routes from a queue, one reply per request, and fails loudly when it runs dry. */
    private static StubServer scripted(Reply... replies) throws IOException {
        Deque<Reply> queue = new ArrayDeque<>(List.of(replies));
        StubServer server = new StubServer();
        server.on("/api/v1/assets", (request, exchange) -> answer(queue, exchange));
        return server;
    }

    private static void answer(Deque<Reply> queue, HttpExchange exchange) throws IOException {
        Reply reply = queue.poll();
        if (reply == null) {
            StubServer.respond(exchange, 500, "{\"code\":500,\"msg\":\"unexpected request\",\"request_id\":\"x\"}");
            return;
        }
        reply.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        StubServer.respond(exchange, reply.status(), reply.body());
    }

    private static SpicyClient client(StubServer server, int maxRetries) {
        return server.clientBuilder().maxRetries(maxRetries).build();
    }

    private static Map<String, String> query(StubServer.Recorded request) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        String path = request.path();
        int at = path.indexOf('?');
        if (at < 0) {
            return values;
        }
        for (String pair : path.substring(at + 1).split("&")) {
            int eq = pair.indexOf('=');
            values.put(pair.substring(0, eq),
                    java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8));
        }
        return values;
    }

    private static String pathOnly(StubServer.Recorded request) {
        int at = request.path().indexOf('?');
        return at < 0 ? request.path() : request.path().substring(0, at);
    }

    // ── create ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("create posts the source and generates a fresh Idempotency-Key on every call")
    void createGeneratesAKey() throws Exception {
        try (StubServer server = scripted(Reply.ok(asset("ast_hero")), Reply.ok(asset("ast_hero")))) {
            SpicyAssets assets = server.client().assets();

            Asset saved = assets.create(CreateAssetRequest.fromTaskOutput("job_1", 0));
            assets.create(CreateAssetRequest.fromTaskOutput("job_1", 0));

            assertEquals("spicy://a/ast_hero", saved.uri());
            assertEquals(AssetKind.IMAGE, saved.kind());
            assertEquals(AssetSource.TASK_OUTPUT, saved.source());
            StubServer.Recorded first = server.recorded().get(0);
            assertEquals("POST", first.method());
            assertEquals("/api/v1/assets", first.path());
            JsonNode body = MAPPER.readTree(first.body());
            assertEquals("task_output", body.get("source").asText());
            assertEquals("job_1", body.get("taskId").asText());
            assertEquals(0, body.get("index").asInt());
            assertFalse(body.has("key"), "unset fields are absent, not null");
            assertFalse(body.has("name"));

            String one = first.header("Idempotency-Key");
            String two = server.recorded().get(1).header("Idempotency-Key");
            assertTrue(UUID.matcher(one).matches(), one);
            assertTrue(UUID.matcher(two).matches(), two);
            assertNotEquals(one, two, "a constant key would replay the first save instead of making a second");
        }
    }

    @Test
    @DisplayName("a caller's Idempotency-Key is sent unchanged, and a blank or oversized one never leaves")
    void createWithACallerKey() throws Exception {
        try (StubServer server = scripted(Reply.ok(asset("ast_still")))) {
            SpicyAssets assets = server.client().assets();

            assets.create(CreateAssetRequest.fromUrl("https://cdn.example.test/still.png").withName("Still"),
                    "save-still-1");
            assertEquals("save-still-1", server.first("/assets").header("Idempotency-Key"));
            JsonNode body = MAPPER.readTree(server.first("/assets").body());
            assertEquals("url", body.get("source").asText());
            assertEquals("https://cdn.example.test/still.png", body.get("url").asText());
            assertEquals("Still", body.get("name").asText());

            CreateAssetRequest upload = CreateAssetRequest.fromUpload("fil_1");
            assertThrows(IllegalArgumentException.class, () -> assets.create(upload, "   "),
                    "a blank key is a mistake to report, not one to paper over with a generated key");
            assertThrows(IllegalArgumentException.class, () -> assets.create(upload, "k".repeat(129)));
            assertThrows(IllegalArgumentException.class, () -> assets.create(upload, "a\r\nb"));
            assertEquals(1, server.count("/assets"), "none of the refused keys reached the service");
        }
    }

    @Test
    @DisplayName("a transport retry of create replays the same Idempotency-Key")
    void createRetryKeepsTheKey() throws Exception {
        try (StubServer server = scripted(Reply.fail(503, null), Reply.ok(asset("ast_up")))) {
            server.client().assets().create(CreateAssetRequest.fromUpload("spicy://f/fil_1"));

            List<StubServer.Recorded> calls = server.recorded();
            assertEquals(2, calls.size());
            assertTrue(UUID.matcher(calls.get(0).header("Idempotency-Key")).matches());
            assertEquals(calls.get(0).header("Idempotency-Key"), calls.get(1).header("Idempotency-Key"),
                    "a retry under a new key could save the same file twice");
            assertEquals("spicy://f/fil_1", MAPPER.readTree(calls.get(1).body()).get("fileId").asText());
        }
    }

    @Test
    @DisplayName("each factory fills only its own source's fields, and an incomplete source is refused locally")
    void createRequestShapes() throws Exception {
        assertEquals("{\"source\":\"task_output\",\"taskId\":\"job_1\",\"key\":\"out-0\"}",
                MAPPER.writeValueAsString(CreateAssetRequest.fromTaskOutput(" job_1 ", "out-0")));
        assertEquals("{\"source\":\"upload\",\"fileId\":\"fil_1\",\"name\":\"Voice\"}",
                MAPPER.writeValueAsString(CreateAssetRequest.fromUpload("fil_1").withName("Voice")));

        assertThrows(IllegalArgumentException.class, () -> CreateAssetRequest.fromTaskOutput(" ", 0));
        assertThrows(IllegalArgumentException.class, () -> CreateAssetRequest.fromTaskOutput("job_1", -1));
        assertThrows(IllegalArgumentException.class, () -> CreateAssetRequest.fromTaskOutput("job_1", "  "));
        assertThrows(IllegalArgumentException.class, () -> CreateAssetRequest.fromUpload(""));
        assertThrows(IllegalArgumentException.class, () -> CreateAssetRequest.fromUrl(null));
        assertThrows(IllegalArgumentException.class,
                () -> new CreateAssetRequest(AssetSource.UPLOAD, "job_1", null, null, null, null, null),
                "an upload save without a fileId cannot be what the caller meant");
        assertThrows(IllegalArgumentException.class,
                () -> new CreateAssetRequest(AssetSource.UNKNOWN, null, null, null, "fil_1", null, null));
    }

    // ── list and listAll ────────────────────────────────────────────────

    @Test
    @DisplayName("list encodes every filter, joins several sources with commas, and sends nothing when unset")
    void listEncodesEveryFilter() throws Exception {
        try (StubServer server = scripted(Reply.ok(page("c2", "ast_hero")), Reply.ok(page(null)))) {
            SpicyAssets assets = server.client().assets();

            AssetPage first = assets.list(AssetFilter.all()
                    .withKind(AssetKind.IMAGE)
                    .withSources(AssetSource.TASK_OUTPUT, AssetSource.URL)
                    .withSearch("hero portrait")
                    .withFavorite(true)
                    .withCollectionId(" col_moodboard ")
                    .withEntityId("ety_mia")
                    .withSort(AssetSort.NAME)
                    .withCursor("c1")
                    .withLimit(20));
            AssetPage last = assets.list();

            assertTrue(first.hasMore());
            assertEquals("c2", first.nextCursor());
            assertEquals("ast_hero", first.items().get(0).id());
            assertFalse(last.hasMore());
            assertTrue(last.items().isEmpty());

            StubServer.Recorded sent = server.recorded().get(0);
            assertEquals("GET", sent.method());
            assertEquals("/api/v1/assets", pathOnly(sent));
            Map<String, String> query = query(sent);
            assertEquals("image", query.get("kind"));
            assertEquals("task_output,url", query.get("source"));
            assertEquals("hero portrait", query.get("q"));
            assertEquals("true", query.get("favorite"));
            assertEquals("col_moodboard", query.get("collectionId"), "ids are trimmed before they are sent");
            assertEquals("ety_mia", query.get("entityId"));
            assertEquals("name", query.get("sort"));
            assertEquals("c1", query.get("cursor"));
            assertEquals("20", query.get("limit"));
            assertEquals("/api/v1/assets", server.recorded().get(1).path(), "no filters means no query string");
        }
    }

    @Test
    @DisplayName("favorite=false is a filter of its own and is sent; a null favorite is left out")
    void favoriteFalseIsSent() throws Exception {
        try (StubServer server = scripted(Reply.ok(page(null)), Reply.ok(page(null)), Reply.ok(page(null)))) {
            SpicyAssets assets = server.client().assets();

            assets.list(AssetFilter.all().withFavorite(false).withSort(AssetSort.SIZE));
            assets.list(AssetFilter.all().withFavorite(true).withFavorite(null));
            assets.list(AssetFilter.all().withSources());

            Map<String, String> notFavorites = query(server.recorded().get(0));
            assertEquals("false", notFavorites.get("favorite"),
                    "dropping false would turn 'only the non-favorites' into 'everything'");
            assertEquals("size", notFavorites.get("sort"));
            assertFalse(notFavorites.containsKey("collectionId"));
            assertEquals("/api/v1/assets", server.recorded().get(1).path(), "a cleared favorite is not sent");
            assertEquals("/api/v1/assets", server.recorded().get(2).path(), "an empty source list is not sent");
        }
    }

    @Test
    @DisplayName("a blank folder or character id never leaves, and an unknown folder's 404 is thrown as-is")
    void blankFilterIdsAndUnknownFolder() throws Exception {
        try (StubServer server = scripted(Reply.fail(404, null))) {
            SpicyClient client = client(server, 2);

            assertThrows(IllegalArgumentException.class,
                    () -> client.assets().list(AssetFilter.all().withCollectionId("  ")));
            assertThrows(IllegalArgumentException.class,
                    () -> client.assets().list(AssetFilter.all().withEntityId("")));
            assertThrows(IllegalArgumentException.class,
                    () -> client.assets().list(AssetFilter.all().withCollectionId("col\nx")));
            assertThrows(IllegalArgumentException.class, () -> AssetFilter.all().withLimit(0));
            assertThrows(IllegalArgumentException.class, () -> AssetFilter.all().withKind(AssetKind.UNKNOWN));
            assertThrows(IllegalArgumentException.class,
                    () -> AssetFilter.all().withSources(AssetSource.UPLOAD, AssetSource.UNKNOWN));
            assertEquals(0, server.recorded().size(), "every refusal above happened before any request");

            SpicyApiException missing = assertThrows(SpicyApiException.class,
                    () -> client.assets().list(AssetFilter.all().withCollectionId("col_elsewhere")));
            assertEquals(404, missing.httpStatus());
            assertEquals(404, missing.businessCode());
            assertEquals("req_fail", missing.requestId());
            assertEquals(1, server.count("/assets"), "a 404 is an answer, not turbulence, and is not retried");
        }
    }

    @Test
    @DisplayName("an unknown sort is refused locally, and a known one resolves whatever its case")
    void sortIsValidatedLocally() throws Exception {
        try (StubServer server = scripted(Reply.ok(page(null)))) {
            SpicyAssets assets = server.client().assets();

            assertThrows(IllegalArgumentException.class,
                    () -> assets.list(AssetFilter.all().withSort(AssetSort.fromWireValue("newest"))));
            assertThrows(IllegalArgumentException.class, () -> AssetSort.fromWireValue(" "));
            assertThrows(IllegalArgumentException.class, () -> AssetSort.fromWireValue(null));
            assertEquals(0, server.recorded().size(), "a typo in the sort costs no round trip");

            assets.list(AssetFilter.all().withSort(AssetSort.fromWireValue(" Oldest ")));
            assertEquals("oldest", query(server.first("/assets")).get("sort"));
        }
    }

    @Test
    @DisplayName("listAll follows nextCursor to the last page and repeats every filter and the sort on each")
    void listAllKeepsTheFilterAndSort() throws Exception {
        try (StubServer server = scripted(
                Reply.ok(page("c2", "ast_1", "ast_2")),
                Reply.ok(page("c3", "ast_3")),
                Reply.ok(page(null, "ast_4")))) {
            AssetFilter filter = AssetFilter.all()
                    .withKind(AssetKind.VIDEO).withFavorite(true).withSort(AssetSort.NAME).withLimit(2);

            List<String> ids = new ArrayList<>();
            for (Asset item : server.client().assets().listAll(filter)) {
                ids.add(item.id());
            }

            assertEquals(List.of("ast_1", "ast_2", "ast_3", "ast_4"), ids);
            List<String> cursors = new ArrayList<>();
            for (StubServer.Recorded call : server.recorded()) {
                Map<String, String> query = query(call);
                cursors.add(query.get("cursor"));
                assertEquals("video", query.get("kind"), call.path());
                assertEquals("true", query.get("favorite"), call.path());
                assertEquals("name", query.get("sort"),
                        "a cursor only works with the sort it came from, so every page must repeat it");
                assertEquals("2", query.get("limit"), call.path());
            }
            assertEquals(java.util.Arrays.asList(null, "c2", "c3"), cursors);
        }
    }

    @Test
    @DisplayName("listAll fetches nothing more once the caller stops, and refuses a cursor that repeats")
    void listAllStopsAndRefusesALoop() throws Exception {
        try (StubServer server = scripted(Reply.ok(page("c2", "ast_1", "ast_2")))) {
            for (Asset item : server.client().assets().listAll()) {
                assertEquals("ast_1", item.id());
                break;
            }
            assertEquals(1, server.recorded().size(), "breaking out must not fetch the next page");
        }

        try (StubServer server = scripted(Reply.ok(page("same", "ast_1")), Reply.ok(page("same", "ast_2")))) {
            List<String> seen = new ArrayList<>();
            Iterator<Asset> walk = server.client().assets().listAll().iterator();
            SpicyProtocolException loop = assertThrows(SpicyProtocolException.class, () -> {
                while (walk.hasNext()) {
                    seen.add(walk.next().id());
                }
            });

            assertTrue(loop.getMessage().contains("asset listing returned a cursor it had already returned"),
                    loop.getMessage());
            assertEquals(List.of("ast_1", "ast_2"), seen, "the items on the repeating page are still handed out");
            assertEquals(2, server.recorded().size(), "the repeated cursor itself is never requested");
        }
    }

    @Test
    @DisplayName("a cursor sent with another sort is a 400 thrown as-is and never retried")
    void cursorFromAnotherSortIsNotRetried() throws Exception {
        try (StubServer server = scripted(Reply.fail(400, null))) {
            SpicyClient client = client(server, 3);

            SpicyApiException stale = assertThrows(SpicyApiException.class,
                    () -> client.assets().list(AssetFilter.all().withSort(AssetSort.OLDEST).withCursor("name-c2")));

            assertEquals(400, stale.httpStatus());
            assertEquals(400, stale.businessCode());
            assertEquals("req_fail", stale.requestId());
            assertFalse(stale.transientFailure());
            assertEquals(1, server.recorded().size(), "resending a rejected cursor cannot succeed");
            Map<String, String> sent = query(server.first("/assets"));
            assertEquals("oldest", sent.get("sort"));
            assertEquals("name-c2", sent.get("cursor"), "the cursor is passed through unchanged");
        }
    }

    // ── get, update, delete, usage ──────────────────────────────────────

    @Test
    @DisplayName("get, update and delete address one asset by its bare id or by its spicy://a/ reference")
    void addressingOneAsset() throws Exception {
        try (StubServer server = scripted(
                Reply.ok(asset("ast_hero")),
                Reply.ok(asset("ast_hero", "Hero v2", false)),
                Reply.ok("{\"id\":\"ast_hero\",\"deleted\":true}"))) {
            SpicyAssets assets = server.client().assets();

            Asset one = assets.get("ast_hero");
            Asset renamed = assets.update("spicy://a/ast_hero", "Hero v2", null);
            AssetDeleted deleted = assets.delete("  ast_hero  ");

            assertEquals("https://preview.example.test/private", one.preview().orElseThrow());
            assertEquals("publisher/model-5.0/text-to-image", one.generatedBy().orElseThrow());
            assertEquals("Hero v2", renamed.name());
            assertEquals("ast_hero", deleted.id());
            assertTrue(deleted.deleted());
            List<String> calls = server.recorded().stream().map(r -> r.method() + " " + r.path()).toList();
            assertEquals(List.of("GET /api/v1/assets/ast_hero", "PATCH /api/v1/assets/ast_hero",
                    "DELETE /api/v1/assets/ast_hero"), calls);
            assertEquals("{\"name\":\"Hero v2\"}", server.recorded().get(1).body());
            assertFalse(server.recorded().get(1).hasHeader("Idempotency-Key"),
                    "rename is safe to repeat and carries no key");
            assertFalse(server.recorded().get(2).hasHeader("Idempotency-Key"),
                    "delete is safe to repeat and carries no key");
        }
    }

    @Test
    @DisplayName("update sends only the fields given, and refuses a call that gives neither")
    void updateSendsOnlyWhatIsGiven() throws Exception {
        try (StubServer server = scripted(
                Reply.ok(asset("ast_hero", "Hero portrait", true)),
                Reply.ok(asset("ast_hero", "Hero v3", false)))) {
            SpicyAssets assets = server.client().assets();

            Asset starred = assets.update("spicy://a/ast_hero", null, true);
            assets.update("ast_hero", "Hero v3", false);

            assertTrue(starred.favorite());
            JsonNode toggle = MAPPER.readTree(server.recorded().get(0).body());
            assertTrue(toggle.get("favorite").asBoolean());
            assertFalse(toggle.has("name"), "a favorite-only update must leave the name alone");
            assertEquals("{\"name\":\"Hero v3\",\"favorite\":false}", server.recorded().get(1).body(),
                    "false is a value to send, not an absence");

            IllegalArgumentException neither =
                    assertThrows(IllegalArgumentException.class, () -> assets.update("ast_hero", null, null));
            assertTrue(neither.getMessage().contains("name, a favorite flag, or both"), neither.getMessage());
            assertEquals(2, server.recorded().size(), "the empty update never left the process");
        }
    }

    @Test
    @DisplayName("asset ids are path-encoded, and an empty id or reference is refused before any request")
    void assetIdsAreEncodedAndValidated() throws Exception {
        try (StubServer server = scripted(Reply.ok(asset("ast_hero")))) {
            SpicyAssets assets = server.client().assets();

            assets.get("ast_a/../b");
            assertEquals("/api/v1/assets/ast_a%2F..%2Fb", server.first("/assets").path(),
                    "a slash in an id must not become a different path");

            assertThrows(IllegalArgumentException.class, () -> assets.get("spicy://a/"));
            assertThrows(IllegalArgumentException.class, () -> assets.get(null));
            assertThrows(IllegalArgumentException.class, () -> assets.delete(" "));
            assertThrows(IllegalArgumentException.class, () -> assets.update("", "name", null));
            assertThrows(IllegalArgumentException.class, () -> assets.get("ast_a\r\nX-Injected: 1"));
            assertEquals(1, server.recorded().size(), "a blank id would have addressed the whole collection");
        }
    }

    @Test
    @DisplayName("an unknown asset is a 404 thrown as-is and not retried")
    void unknownAssetIsA404() throws Exception {
        try (StubServer server = scripted(Reply.fail(404, null))) {
            SpicyApiException missing =
                    assertThrows(SpicyApiException.class, () -> client(server, 3).assets().get("ast_gone"));

            assertEquals(404, missing.httpStatus());
            assertTrue(missing.reason().isEmpty());
            assertEquals(1, server.recorded().size());
        }
    }

    @Test
    @DisplayName("usage maps the plan, the limits and the scope, and the two amounts are optional")
    void usage() throws Exception {
        String plan = "{\"id\":\"plus\",\"name\":\"Plus\",\"minTopupUsd\":\"10\",\"maxBytes\":524288000,"
                + "\"maxCount\":200,\"maxFileBytes\":104857600}";
        String limits = "{\"tier\":\"plus\",\"maxCount\":200,\"maxBytes\":524288000,\"maxFileBytes\":104857600,"
                + "\"globalMaxBytes\":0,\"nameMaxChars\":80}";
        String owner = "{\"tier\":" + plan + ",\"usage\":{\"count\":3,\"bytes\":4096},\"limits\":" + limits
                + ",\"full\":false,\"nextTier\":null,\"tiers\":[" + plan + "],\"dailyCreateLimit\":200,"
                + "\"createdToday\":1,\"lifetimeTopupUsd\":\"25\",\"scope\":{\"workspaceId\":\"ws_me\","
                + "\"personal\":true,\"role\":\"owner\",\"canWrite\":true,\"canTopUp\":true}}";
        String developer = "{\"tier\":" + plan + ",\"usage\":{\"count\":3,\"bytes\":4096},\"limits\":" + limits
                + ",\"full\":false,\"nextTier\":{\"id\":\"pro\",\"name\":\"Pro\",\"minTopupUsd\":\"100\","
                + "\"maxBytes\":1073741824,\"maxCount\":400,\"maxFileBytes\":104857600},\"tiers\":[" + plan + "],"
                + "\"dailyCreateLimit\":200,\"createdToday\":0,\"scope\":{\"workspaceId\":\"ws_team\","
                + "\"personal\":false,\"role\":\"developer\",\"canWrite\":true,\"canTopUp\":false}}";
        try (StubServer server = scripted(Reply.ok(owner), Reply.ok(developer))) {
            SpicyAssets assets = server.client().assets();

            AssetUsage mine = assets.usage();
            assertEquals("/api/v1/assets/usage", server.first("/assets").path());
            assertEquals("GET", server.first("/assets").method());
            assertEquals("Plus", mine.tier().name());
            assertEquals(3L, mine.stored().count());
            assertEquals(4096L, mine.stored().bytes());
            assertEquals(80, mine.limits().nameMaxChars());
            assertEquals(524288000L, mine.limits().maxBytes());
            assertEquals(Optional.of("25"), mine.lifetimeTopup());
            assertTrue(mine.next().isEmpty(), "null on the highest plan is 'no next plan', not a parse failure");
            assertTrue(mine.scope().personal());
            assertEquals(1, mine.tiers().size());

            AssetUsage team = assets.usage();
            assertFalse(team.scope().canTopUp());
            assertEquals("developer", team.scope().role());
            assertTrue(team.lifetimeTopup().isEmpty(), "omitted when the caller may not top up");
            assertTrue(team.topupToNextTier().isEmpty());
            assertEquals("Pro", team.next().orElseThrow().name());
        }
    }

    // ── characters ──────────────────────────────────────────────────────

    @Test
    @DisplayName("characters.list reads one page, and characters.get one character with its images in order")
    void charactersListAndGet() throws Exception {
        try (StubServer server = scripted(
                Reply.ok("{\"items\":[" + character("ety_mia") + "],\"nextCursor\":\"ec2\"}"),
                Reply.ok(character("ety_mia")),
                Reply.ok("{\"items\":[]}"))) {
            SpicyAssetCharacters characters = server.client().assets().characters();

            AssetCharacterPage first = characters.list(AssetCharacterFilter.all()
                    .withAssetId("spicy://a/ast_front").withLimit(10).withCursor("ec1"));
            AssetCharacter mia = characters.get(" ety_mia ");
            AssetCharacterPage empty = characters.list();

            assertEquals("ec2", first.nextCursor());
            assertTrue(first.hasMore());
            assertFalse(empty.hasMore());
            assertEquals(List.of("spicy://a/ast_front", "spicy://a/ast_side"), mia.uris());
            assertEquals("ast_front", mia.coverImage().orElseThrow().id());
            assertEquals(2, mia.count());

            StubServer.Recorded listed = server.recorded().get(0);
            assertEquals("/api/v1/assets/entities", pathOnly(listed));
            assertEquals("ast_front", query(listed).get("assetId"), "a spicy://a/ reference is unwrapped");
            assertEquals("10", query(listed).get("limit"));
            assertEquals("ec1", query(listed).get("cursor"));
            assertEquals("GET /api/v1/assets/entities/ety_mia",
                    server.recorded().get(1).method() + " " + server.recorded().get(1).path());
            assertEquals("/api/v1/assets/entities", server.recorded().get(2).path(), "no filters, no query string");
        }
    }

    @Test
    @DisplayName("a kind or source added after this release parses as UNKNOWN, and new fields are ignored")
    void unknownValuesParse() throws Exception {
        Asset future = MAPPER.readValue("""
                {"id":"ast_doc","uri":"spicy://a/ast_doc","name":"Brief","kind":"document",
                 "contentType":"application/pdf","bytes":10,"source":"import","sourceId":"x",
                 "createdAt":"2026-10-12T08:00:00Z","updatedAt":"2026-10-12T08:00:00Z","previewUrl":"",
                 "favorite":false,"model":"","modelName":"","someFieldAddedNextYear":true}""", Asset.class);

        assertEquals(AssetKind.UNKNOWN, future.kind());
        assertEquals(AssetSource.UNKNOWN, future.source());
        assertTrue(future.preview().isEmpty(), "an empty preview means signing failed, not a link");
        assertTrue(future.generatedBy().isEmpty(), "an empty model means the asset was not generated");
    }

    @Test
    @DisplayName("a character without images has no cover, and that is not a parse failure")
    void characterWithoutImages() throws Exception {
        AssetCharacter empty = MAPPER.readValue("""
                {"id":"ety_new","kind":"character","name":"New","note":"","coverAssetId":"","cover":null,
                 "assets":[],"count":0,"createdAt":"2026-10-12T08:00:00Z","updatedAt":"2026-10-12T08:00:00Z"}""",
                AssetCharacter.class);

        assertTrue(empty.coverImage().isEmpty());
        assertTrue(empty.uris().isEmpty());
    }

    @Test
    @DisplayName("characters.listAll follows nextCursor and refuses a cursor that repeats")
    void charactersListAll() throws Exception {
        try (StubServer server = scripted(
                Reply.ok("{\"items\":[" + character("ety_1") + "],\"nextCursor\":\"ec2\"}"),
                Reply.ok("{\"items\":[" + character("ety_2") + "]}"))) {
            List<String> ids = new ArrayList<>();
            for (AssetCharacter item : server.client().assets().characters()
                    .listAll(AssetCharacterFilter.all().withLimit(1))) {
                ids.add(item.id());
            }

            assertEquals(List.of("ety_1", "ety_2"), ids);
            assertEquals(java.util.Arrays.asList(null, "ec2"),
                    server.recorded().stream().map(r -> query(r).get("cursor")).toList());
            assertTrue(server.recorded().stream().allMatch(r -> "1".equals(query(r).get("limit"))));
        }

        try (StubServer server = scripted(
                Reply.ok("{\"items\":[" + character("ety_1") + "],\"nextCursor\":\"same\"}"),
                Reply.ok("{\"items\":[" + character("ety_2") + "],\"nextCursor\":\"same\"}"))) {
            SpicyProtocolException loop = assertThrows(SpicyProtocolException.class, () -> {
                for (AssetCharacter ignored : server.client().assets().characters().listAll()) {
                    assertTrue(ignored.id().startsWith("ety_"));
                }
            });
            assertTrue(loop.getMessage().contains("character listing returned a cursor it had already returned"));
        }
    }

    @Test
    @DisplayName("an unknown character is a 404 thrown as-is, and a blank id or reference never leaves")
    void unknownOrBlankCharacter() throws Exception {
        try (StubServer server = scripted(Reply.fail(404, null))) {
            SpicyClient client = client(server, 2);

            SpicyApiException missing =
                    assertThrows(SpicyApiException.class, () -> client.assets().characters().get("ety_gone"));
            assertEquals(404, missing.httpStatus());
            assertEquals(404, missing.businessCode());
            assertEquals(1, server.recorded().size(), "a 404 is not retried");
            assertEquals("/api/v1/assets/entities/ety_gone", server.first("/entities").path());

            assertThrows(IllegalArgumentException.class, () -> client.assets().characters().get("   "));
            assertThrows(IllegalArgumentException.class,
                    () -> client.assets().characters().list(AssetCharacterFilter.all().withAssetId("spicy://a/")));
            assertThrows(IllegalArgumentException.class, () -> AssetCharacterFilter.all().withLimit(0));
            assertEquals(1, server.recorded().size());
        }
    }

    // ── reasons ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("data.reason becomes SpicyApiException.reason(), unknown values included and non-strings dropped")
    void reasonsArePassedThrough() throws Exception {
        try (StubServer server = scripted(
                Reply.fail(400, "\"data\":{\"reason\":\"asset_library_full\"}"),
                Reply.fail(400, "\"data\":{\"reason\":\"asset_reason_from_the_future\"}"),
                Reply.fail(400, null),
                Reply.fail(400, "\"data\":{\"reason\":42}"),
                Reply.fail(400, "\"data\":{\"reason\":\"\"}"))) {
            SpicyAssets assets = client(server, 0).assets();
            List<SpicyApiException> failures = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                failures.add(assertThrows(SpicyApiException.class,
                        () -> assets.create(CreateAssetRequest.fromUpload("fil_1"))));
            }

            assertEquals(Optional.of("asset_library_full"), failures.get(0).reason());
            assertEquals(Optional.of(AssetErrorReason.ASSET_LIBRARY_FULL), failures.get(0).assetErrorReason());
            assertTrue(failures.get(0).remediation().contains("allowance is used up"), failures.get(0).remediation());
            assertTrue(failures.get(0).describe().contains("reason=asset_library_full"), failures.get(0).describe());

            assertEquals(Optional.of("asset_reason_from_the_future"), failures.get(1).reason(),
                    "a reason the service adds later still reaches the caller, verbatim");
            assertTrue(failures.get(1).assetErrorReason().isEmpty());
            assertTrue(failures.get(2).reason().isEmpty());
            assertFalse(failures.get(2).describe().contains("reason="));
            assertTrue(failures.get(3).reason().isEmpty(), "a reason that is not a string is no reason");
            assertTrue(failures.get(4).reason().isEmpty(), "an empty reason is no reason");
            for (SpicyApiException failure : failures) {
                assertEquals(400, failure.httpStatus());
                assertEquals(400, failure.businessCode());
                assertEquals("req_fail", failure.requestId());
            }
        }
    }

    @Test
    @DisplayName("a key that cannot reach the library gets a 403 with its reason, thrown as-is and not retried")
    void forbiddenLibrary() throws Exception {
        try (StubServer server = scripted(Reply.fail(403, "\"data\":{\"reason\":\"asset_workspace_forbidden\"}"))) {
            SpicyApiException forbidden =
                    assertThrows(SpicyApiException.class, () -> client(server, 2).assets().list());

            assertEquals(403, forbidden.httpStatus());
            assertEquals(Optional.of(AssetErrorReason.ASSET_WORKSPACE_FORBIDDEN), forbidden.assetErrorReason());
            assertEquals("refused by the asset library", forbidden.getMessage());
            assertTrue(forbidden.remediation().contains("cannot reach that library"), forbidden.remediation());
            assertEquals(1, server.recorded().size(), "a 403 is a decision, not a transient failure");
        }
    }

    @Test
    @DisplayName("the daily limit surfaces at once with its reason and Retry-After, not after a futile wait")
    void dailyLimitFailsFast() throws Exception {
        Reply limited = new Reply(429, failure(429, "\"data\":{\"reason\":\"asset_daily_limit\"}"),
                Map.of("Retry-After", "3600"));
        try (StubServer server = scripted(limited, limited, limited, limited)) {
            SpicyClient client = client(server, 3);
            long started = System.nanoTime();

            SpicyApiException daily = assertThrows(SpicyApiException.class,
                    () -> client.assets().create(CreateAssetRequest.fromUpload("fil_1")));

            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
            assertEquals(Optional.of(AssetErrorReason.ASSET_DAILY_LIMIT), daily.assetErrorReason());
            assertEquals(Optional.of(Duration.ofHours(1)), daily.retryAfter());
            assertEquals(1, server.recorded().size(),
                    "a wait that outlasts the whole request budget cannot lead to another attempt");
            assertTrue(elapsed.compareTo(Duration.ofSeconds(10)) < 0,
                    "the failure must not arrive only after sleeping to the deadline: " + elapsed);
        }
    }

    @Test
    @DisplayName("a 429 with a short Retry-After is still retried as before")
    void shortRetryAfterIsStillRetried() throws Exception {
        Reply limited = new Reply(429, failure(429, null), Map.of("Retry-After", "0"));
        try (StubServer server = scripted(limited, Reply.ok(page(null)))) {
            server.client().assets().list();

            assertEquals(2, server.recorded().size());
        }
    }

    // ── upload and save ─────────────────────────────────────────────────

    private static StubServer uploadAndSave(Reply save) throws IOException {
        StubServer server = new StubServer();
        server.on("/api/v1/common/upload-url", (request, exchange) -> StubServer.respond(exchange, 200,
                StubServer.envelope("""
                        {"fileId":"fil_9","key":"spicy://f/fil_9",
                         "uploadUrl":"http://127.0.0.1:%d/storage/put?sig=abc","method":"PUT",
                         "headers":{"Content-Type":"image/png"},
                         "expiresAt":"2026-10-12T00:10:00Z","maxBytes":10485760}""".formatted(server.port()))));
        server.on("/storage/put", (request, exchange) -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.onData("/api/v1/files/", """
                {"fileId":"fil_9","status":"ready","bytes":4,"contentType":"image/png",
                 "sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                 "uri":"spicy://f/fil_9","expiresAt":"2026-10-13T00:00:00Z"}""");
        Deque<Reply> queue = new ArrayDeque<>(List.of(save));
        server.on("/api/v1/assets", (request, exchange) -> answer(queue, exchange));
        return server;
    }

    @Test
    @DisplayName("uploadFile uploads, commits and saves under the file name")
    void uploadFileSavesUnderTheFileName(@TempDir Path directory) throws Exception {
        Path file = Files.write(directory.resolve("hero portrait.png"), new byte[] {(byte) 137, 80, 78, 71});
        try (StubServer server = uploadAndSave(Reply.ok(asset("ast_up")))) {
            Asset saved = server.client().assets().uploadFile(file);

            assertEquals("ast_up", saved.id());
            assertFalse(server.first("/storage/put").hasHeader("Authorization"),
                    "the storage hop never carries the key");
            StubServer.Recorded save = server.first("/api/v1/assets");
            assertEquals("POST", save.method());
            JsonNode body = MAPPER.readTree(save.body());
            assertEquals("upload", body.get("source").asText());
            assertEquals("fil_9", body.get("fileId").asText());
            assertEquals("hero portrait.png", body.get("name").asText());
            assertTrue(UUID.matcher(save.header("Idempotency-Key")).matches());
        }
    }

    @Test
    @DisplayName("uploadFile takes an explicit name, cuts a long default on code points, and saves nothing when the upload fails")
    void uploadFileNamesAndFailures(@TempDir Path directory) throws Exception {
        Path clip = Files.write(directory.resolve("clip.png"), new byte[] {1, 2, 3, 4});
        try (StubServer server = uploadAndSave(Reply.ok(asset("ast_clip")))) {
            server.client().assets().uploadFile(clip, "Opening shot");

            assertEquals("Opening shot", MAPPER.readTree(server.first("/api/v1/assets").body()).get("name").asText());
        }

        // 79 letters, then an emoji that takes two chars, then more: the default name is cut to 80
        // code points, so it ends on the whole emoji rather than on half of it.
        String emoji = "\uD83D\uDE00";
        Path longName = Files.write(directory.resolve("a".repeat(79) + emoji + "tail.png"), new byte[] {1, 2, 3, 4});
        try (StubServer server = uploadAndSave(Reply.ok(asset("ast_long")))) {
            server.client().assets().uploadFile(longName);

            String name = MAPPER.readTree(server.first("/api/v1/assets").body()).get("name").asText();
            assertEquals("a".repeat(79) + emoji, name);
            assertEquals(80, name.codePointCount(0, name.length()));
        }

        try (StubServer server = new StubServer()) {
            server.on("/api/v1/common/upload-url", (request, exchange) -> StubServer.respond(exchange, 200,
                    StubServer.envelope("""
                            {"fileId":"fil_6","key":"spicy://f/fil_6",
                             "uploadUrl":"http://127.0.0.1:%d/storage/put","method":"PUT","headers":{},
                             "expiresAt":"2026-10-12T00:10:00Z","maxBytes":10485760}""".formatted(server.port()))));
            server.on("/storage/put", (request, exchange) -> {
                exchange.sendResponseHeaders(403, -1);
                exchange.close();
            });
            server.on("/api/v1/assets", (request, exchange) -> StubServer.respond(exchange, 200,
                    StubServer.envelope(asset("ast_never"))));

            assertThrows(SpicyUploadException.class, () -> server.client().assets().uploadFile(clip));
            assertEquals(0, server.count("/api/v1/assets"), "nothing is saved when the bytes never arrived");
            assertEquals(0, server.count("/files/"), "nor is anything committed");
        }
    }
}
