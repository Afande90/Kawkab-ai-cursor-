# Backup archive format

A Knotwork backup is one file, locked with a password the user chooses. This page
describes the file byte by byte, so that it can be read without the app.

> **Status: format 1 is defined and tested.** Export and restore are being built
> on it; the entries an export writes are added to [Content](#content) when export
> ships. From the first release that writes archives, every format version stays
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
| Writer version name | n | UTF-8, e.g. `0.12.0` |
| Body length | 4 | 1 to 4096 |
| Body | n | below |
| MAC | 32 | HMAC-SHA256 under the header key, over every byte above |

The fields up to the body length are the same in every format version, so a
reader of an older format can still say which build wrote a newer file.

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
| Damaged | a chunk does not authenticate, has an impossible length or flags, a non-last chunk is short, bytes follow the last chunk, or the content is not a valid entry list |
| Incomplete | the file ends before the chunk flagged as last |

## Content

The decrypted content is a ZIP stream. Every entry is deflated — at level 0 when
compressing would not help (the database, images).

- The first entry is `manifest.json`. Content without it is damaged.
- Every entry is a file, named once. A name is a relative path of `/`-separated
  segments of at most 1024 characters, with no leading `/`, no `\`, no empty,
  `.` or `..` segment and no control character. Anything else is damaged.
- A reader unpacks into a staging place and replaces nothing until the last
  chunk has authenticated: before that, a later chunk can still turn out damaged.

## What the format does not hide

The header is readable without the password: the version of the build that wrote
the file, its flavour, the database schema version, when the file was written,
the estimated content size and the key costs. Everything else — every entry name
and every byte of content — is encrypted.

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
