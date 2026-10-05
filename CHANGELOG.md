# Changelog

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
