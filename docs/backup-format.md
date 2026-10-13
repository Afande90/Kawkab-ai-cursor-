# Backup archive format

A Knotwork backup is one file, locked with a password the user chooses. This page
describes the file byte by byte, so that it can be read without the app.

> **Status: format 1 is defined and tested, and so is the export that writes it.**
> Restore is being built on it, and neither is in the app's screens yet. From the first release that writes archives, every format version stays
> readable: a reader that meets a newer version refuses the file instead of
> guessing.

The file has three parts, in this order:

1. an open **header** that names how the keys are derived;
2. the **content**, a ZIP stream of named entries;
3. encrypted in numbered **chunks** with AES-256-GCM.

Nothing in the file can be read or changed without the password, except the
header fields listed in [What the format does not hide](#what-the-format-does-not-hide).

## Keys

The password is normalised to Unicode NFC and encoded as UTF-8, so the same
password typed on another keyboard opens the same file. Argon2id (RFC 9106,
version 0x13) derives 64 bytes from it with the header's salt and costs:

- bytes 0–31: the **content key** (AES-256);
- bytes 32–63: the **header key** (HMAC-SHA256).

Argon2id runs without RFC 9106's optional secret and associated data. The app
writes with 64 MiB of memory, 3 passes and 1 lane — a median of one second on the
phone it was measured on (Galaxy S25 Ultra, Android 16). A reader takes the costs from the header and refuses a file that
asks for more than 256 MiB, 10 passes or 4 lanes, or less than the 8 KiB per lane
RFC 9106 requires.

## Header

All integers are big-endian.

| Field | Size | Value |
|---|---|---|
| Magic | 8 | ASCII `KNOTWORK` |
| Format version | 2 | `1` |
| Writer version name length | 2 | at most 256 |
| Writer version name | n | a version, e.g. `0.12.0`: 1 to 32 ASCII letters, digits, `.`, `+` or `-` |
| Body length | 4 | 1 to 4096 |
| Body | n | below |
| MAC | 32 | HMAC-SHA256 under the header key, over every byte above |

The fields up to the body length are the same in every format version, so a
reader of an older format can still say which build wrote a newer file. That name
is read before anything is authenticated, so a reader shows it only when it looks
like a version; a format-1 header whose name does not is damaged.

Body of format 1:

| Field | Size | Value |
|---|---|---|
| KDF | 1 | `1` = Argon2id, version 0x13 |
| Memory | 4 | KiB |
| Passes | 4 | |
| Lanes | 1 | |
| Salt length | 1 | `16` |
| Salt | 16 | random |
| Chunk size | 4 | plaintext bytes per chunk; the app writes 1 MiB, a reader accepts 4 KiB to 16 MiB |
| Content bytes | 8 | the writer's estimate of the unpacked content, for a free-space check |
| Schema version | 4 | of the database inside |
| Created | 8 | milliseconds since the Unix epoch |
| Writer version code | 4 | |
| Flavour length | 2 | at most 64 |
| Flavour | n | UTF-8, `full` or `foss`; informational — an archive restores into either |

A later writer of format 1 may append fields to the body; a reader ignores bytes
after the ones it knows. The MAC covers them.

A reader checks the MAC before it decrypts anything. A MAC that does not match
means a wrong password — or a header altered after it was written; the two cannot
be told apart, and neither file opens.

## Chunks

After the header come the chunks, each in a frame:

| Field | Size | Value |
|---|---|---|
| Flags | 1 | bit 0 set on the last chunk; every other bit zero |
| Sealed length | 4 | 28 to chunk size + 28 |
| Sealed chunk | n | 12-byte IV, ciphertext, 16-byte tag |

Every chunk but the last holds exactly *chunk size* bytes of content; the last
holds the rest, possibly a full chunk, possibly nothing. The associated data of
chunk *i* (from 0) is:

```
SHA-256(header bytes before the MAC) ‖ i as 8 bytes ‖ the flags byte
```

So a chunk moved to another position, copied from another archive or relabelled
as the last one does not authenticate. The file ends right after the last chunk.

A reader hands out a chunk's content only after the chunk has authenticated, and
reports:

| Report | When |
|---|---|
| Not a backup | the file does not start with the magic |
| Newer format | the format version is above the one the reader knows |
| Too demanding | the key costs are out of bounds, or the device cannot spare the memory |
| Wrong password | the header's MAC does not match |
| Damaged | a header field is out of range (format version 0, an unknown KDF, a salt that is not 16 bytes, a chunk size, body length, name or flavour out of its bounds, a negative content size, a body that ends early); a chunk does not authenticate, has an impossible length or flags, or is short without being the last; bytes follow the last chunk; or the content is not a valid entry list |
| Incomplete | the file ends inside the header or before the chunk flagged as last, or continues with zeros where a chunk should start — as a copy that stopped does |

The first failure is final: a reader that is asked again reports it again and
hands out nothing that follows.

## Content

The decrypted content is a ZIP stream. Every entry is deflated — at level 0 when
compressing would not help (the database, images).

- The first entry is `manifest.json`. Content without it is damaged.
- Every entry is a file, named once. A name is a relative path of `/`-separated
  segments, at most 1024 UTF-16 code units, with no leading `/`, no `\`, no
  empty, `.` or `..` segment, no control character (C0, C1 or DEL) and no
  bidirectional formatting character. A name may not also be the directory of
  another (`files/a` and `files/a/b`). Anything else is damaged, and so is
  content that is not a well-formed ZIP stream.
- A reader unpacks into a staging place and replaces nothing until the last
  chunk has authenticated: before that, a later chunk can still turn out damaged.

## Entries

An export writes these, in this order:

| Entry | What it holds |
|---|---|
| `manifest.json` | What the archive holds ([Manifest](#manifest)) |
| `database/key` | 32 random bytes: the key of the database copy below, made for this archive alone |
| `preferences.json` | The settings that travel, with their types ([Settings](#settings)) |
| `providers.json` | Each cloud provider's chosen model and server address, and the Ollama context window — not secrets, so always present |
| `secrets.json` | Cloud API keys, the Hugging Face token, MCP sign-ins and headers — only when the user ticked the box |
| `database/agent_database.db` | A copy of the app's database: SQLCipher, keyed with the bytes of `database/key` as its passphrase, schema version in `user_version` |
| `files/agent_workspace/…` | Every file of the agent's workspace, by its path inside it |
| `files/attachments/…` | Every image attachment, by its file name |

The phone's own database key never goes into an archive: the copy is made under a
key of its own. Server and model addresses travel as they are written, with the
secrets left out — so a token written into an address travels with it. Model files are not in the archive — the manifest lists them.
Tasks the agent scheduled with `schedule_task` live in the background runtime's own
queue and are not carried; the manifest counts them.

### Manifest

```json
{
  "manifestVersion": 1,
  "createdAt": 1791849600000,
  "schemaVersion": 71,
  "secretsIncluded": false,
  "counts": { "chats": 12, "memories": 340, "pipelines": 9, "workspaceFiles": 4, "attachments": 2 },
  "scheduledTasks": 1,
  "skippedFiles": 0,
  "models": [
    { "name": "gemma-4-E2B-it.litertlm", "sizeBytes": 2588147712, "sha256": "…", "sourceUrl": "https://…" }
  ]
}
```

- `skippedFiles` counts workspace files and attachments whose path cannot be an
  entry name; they are not in the archive.
- A model's `sha256` and `sourceUrl` are left out when unknown.
- A reader ignores keys it does not know.

### Settings

`preferences.json`:

```json
{ "preferencesVersion": 1,
  "values": { "temperature": { "type": "float", "value": 0.7 },
              "approved_cleartext_origins": { "type": "stringSet", "value": ["http://192.168.1.5:11434"] } } }
```

The types are `boolean`, `int`, `long`, `float`, `double`, `string`, `stringSet` (a
sorted array) and `byteArray` (Base64). A setting that describes the phone rather
than the user's choices — the inference backend, the first-launch and onboarding
markers, consent to crash reporting — is never written, and the switch that lets
other apps run a pipeline is left for the new phone to decide.

`providers.json` and `secrets.json` share one shape: entries by
the name of the encrypted store they come from.

```json
{ "entriesVersion": 1,
  "stores": { "secure_api_keys_v2": { "openai_model": "gpt-5.4", "ollama_base_url": "http://192.168.1.5:11434" } } }
```

## What the format does not hide

The header is readable without the password: the version name and code of the
build that wrote the file, its flavour, the database schema version, when the file
was written, the estimated content size, the chunk size and the key costs. The
file's length gives away the size of the compressed content. Everything else —
every entry name and every byte of content — is encrypted.

The password is the only defence of a file that leaves the phone. It is never
stored, and there is no recovery: a file whose password is lost cannot be opened,
by anyone.

## Test vector

`app/src/test/resources/backup/format-1.knotwork` is a format-1 archive written
with the app's own key costs, the password `correct horse battery staple` and
4 KiB chunks. Its content is synthetic. Its header names version `0.12.0`
(code 17), flavour `full`, schema 70 and 2026-10-13T00:00:00Z. Its entries, in
order:

| Entry | Bytes | SHA-256 |
|---|---|---|
| `manifest.json` | 111 | `6ec46186efa907ad18d439dfac9748298554670918157ab28e0b60d2f1fa6494` |
| `database/agent_database.db` | 10000 | `94d1c4dee5e7d0a4aee88c94488183c2bc77e4ff7464399f95a0b2a65f819658` |
| `files/agent_workspace/notes/día de campo.md` | 1480 | `98ca56ce70eeb7decea674700df929a0fbdf2452cc8b92ea64d580a96b013227` |
| `files/attachments/3f2a9c.jpg` | 3000 | `d30182d720a93259000d4afdebb992b2f3707a6230f6a63b9c674b50573e96c0` |

`BackupFormatV1FixtureTest` reads it in every build. Once a release has written
format 1, the file never changes; a later format version adds its own beside it.
