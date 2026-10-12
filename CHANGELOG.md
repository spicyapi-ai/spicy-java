# Changelog

## 0.3.0

- Add the asset library: keep files in a library and reference them as `spicy://a/<id>` in any media
  field of model inputs sent with keys of the same workspace, until they are deleted. An upload's
  `spicy://` URI lasts one day; a saved asset does not expire. The key decides the library: a
  personal key reaches the personal library, a team key that team's shared library, where the
  creator's current role decides what may change.
  - `SpicyClient.assets()` returns `SpicyAssets` with `create`, `list`, `listAll` (a lazy
    `Iterable` that follows `nextCursor` and refuses a cursor that repeats), `get`, `update`,
    `delete`, `usage` and `uploadFile`, which uploads a local file and saves it in one call.
    `create` sends the required `Idempotency-Key` itself, a fresh UUID per call unless you pass
    one, which also makes its transport retries safe. Asset ids are accepted as `ast_...` or as the
    `spicy://a/...` reference, and a blank one is refused before any request.
  - `CreateAssetRequest` builds a save from a task output (`fromTaskOutput` by index or key), an
    upload (`fromUpload`) or a public link (`fromUrl`), with an optional `withName`.
  - `AssetFilter` filters by `kind`, `sources`, a name search (`q`), `favorite` (`false` is sent,
    `null` is left out), `collectionId` (one folder; an unknown folder is a 404) and `entityId`
    (one character's images), orders with `AssetSort` (`RECENT`, `OLDEST`, `NAME`, `SIZE`;
    `fromWireValue` refuses anything else), and pages with `cursor` and `limit`. A cursor only works
    with the sort it came from, and `listAll` repeats the filter and the sort on every page.
  - `update(id, name, favorite)` changes the name, the favorite mark or both and sends only what
    you pass; passing neither is refused locally.
  - `assets().characters()` returns `SpicyAssetCharacters` with `list`, `listAll` and `get`,
    read-only, for the named, ordered image sets arranged in the console. `AssetCharacter.uris()`
    gives the images' `spicy://a/` references in order. Personas became characters on 2026-10-12;
    existing `spicy://p/<id>` references keep resolving to the same images.
  - `usage()` returns `AssetUsage`: the plan, what is stored, the limits, the plan table, today's
    count and `scope()` (`AssetScope`: library, role, `canWrite`, `canTopUp`). `lifetimeTopup()` and
    `topupToNextTier()` are optional because they are omitted when `canTopUp` is false.
- `SpicyApiException.reason()` carries a failure envelope's `data.reason` verbatim, and
  `assetErrorReason()` maps it onto `AssetErrorReason`: `asset_library_locked`,
  `asset_library_full`, `asset_file_too_large`, `asset_daily_limit`, `asset_storage_full`,
  `asset_permission_denied`, `asset_workspace_forbidden` and `asset_entity_name_taken`, kept equal
  to the contract's enum by the drift test. `remediation()` and `describe()` take the reason into
  account. A new constructor takes the reason; the previous one is kept.
- A retryable failure whose `Retry-After` outlasts the remaining request budget is now thrown at
  once instead of after sleeping to the deadline. The outcome is the same exception; only the dead
  time is gone. The asset library's daily limit, whose `Retry-After` runs until 00:00 UTC, is the
  case this was for.
- Upload LoRA weights. `UploadContentType.APPLICATION_OCTET_STREAM` is the new upload type for a
  `.safetensors` file (up to 2 GiB; the commit checks that it is a complete safetensors file, and
  the URI goes in `loras[].path`), and `uploadFile` infers it from the `.safetensors` extension.
  `uploadFile(Path, ...)` now streams the file from disk instead of reading it into memory, and
  checks that the file is readable before it claims an upload ticket.
- The pinned contract in `contracts/openapi.yaml` is synced with the published one, including the
  asset routes and `POST /api/v1/files/import`. This release does not wrap the import endpoint.

## 0.2.0

- Upload reference documents. `UploadContentType` gains the twelve document types the upload
  endpoint now accepts — `APPLICATION_PDF`, `APPLICATION_MSWORD`, `APPLICATION_DOCX`,
  `APPLICATION_MS_EXCEL`, `APPLICATION_XLSX`, `APPLICATION_MS_POWERPOINT`, `APPLICATION_PPTX`,
  `APPLICATION_KEYNOTE`, `APPLICATION_PAGES`, `APPLICATION_NUMBERS`, `TEXT_PLAIN` and
  `TEXT_MARKDOWN` — each with a 90 MiB ceiling and extension inference (`.md` and `.markdown` both
  map to Markdown), plus `document()`. Until now a document could not be uploaded through this
  client at all, because `createUploadUrl` takes the enum.
- Speech-to-text results carry their timed transcript: `TaskOutput.transcript()` and the
  `TaskRecord.transcript()` shortcut return a `TaskTranscript` with the detected language, per-word
  timing (`TaskTranscriptWord`) and, for multichannel audio, one `TaskTranscriptChannel` per channel.
  `outputText()` still returns the plain transcript.
- Layer decomposition results can be put back together: `TaskAsset.layer()` returns a
  `TaskAssetLayer` with the layer's `zIndex`, its `name` when the model gives one, and the
  `boundingBox` that places it on the base image.
- `TaskRecord.requestId()` returns the `X-Request-Id` of the request that created the task, which
  matches a synchronous text call to the task that charged it.
- `SpicyApiException.remediation()` explains business code `40310`: the account has not verified
  its email address, so it cannot run models until it does.
- The pinned contract in `contracts/openapi.yaml` is synced with the published one, and the Javadoc
  notes the `per_1k_characters` price unit and the one case where a failed task is billed.
- `TaskOutput`, `TaskAsset` and `TaskRecord` gained a record component each. Their previous
  canonical constructors are kept as secondary constructors, so code that builds them, for example
  in tests, still compiles; the new component is `null` there.

## 0.1.0

- Initial release: the asynchronous task flow, three-step upload, quotes, account history and
  callback signature verification.
