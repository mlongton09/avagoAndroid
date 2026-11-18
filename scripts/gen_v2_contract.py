#!/usr/bin/env python3
"""Generate the Android schema-v2 contract inputs from AvagoSvc.

Outputs (do not hand-edit; rerun this script when the contract changes):

    core/data/src/main/resources/com/avago/core/data/v2/business.sql   (verbatim copy)
    core/data/src/main/resources/com/avago/core/data/v2/chat.sql       (verbatim copy)
    core/data/src/main/kotlin/com/avago/core/data/v2/V2GeneratedSchema.kt

The SQL files are the contract generator's mobile SQLite DDL (decision L29: iOS and Android
apply the same generated file). They are Java resources, not Kotlin strings, because
business.sql is larger than a JVM string constant. V2GeneratedSchema.kt carries the sync
projection manifest (platform.sync_projections, the server's /v2/capabilities answer for this
contract) and the file hashes V2Database checks on open.

Inputs are read from the AvagoSvc repo at a git ref (default origin/schema-v2), never from a
working tree:

    schema/contract/out/sqlite/business.sql
    schema/contract/out/sqlite/chat.sql
    DBSetup/v2/seed/000_sync_projections.sql

Usage (from the repo root):

    git -C ../AvagoSvc fetch origin
    python scripts/gen_v2_contract.py --svc ../AvagoSvc [--ref origin/schema-v2]

Mirrors the iOS generator (avago/scripts/gen_v2_contract.py, P3-11).
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import subprocess
import sys

PKG_DIR = os.path.join("core", "data", "src", "main")
RES_DIR = os.path.join(PKG_DIR, "resources", "com", "avago", "core", "data", "v2")
KT_OUT = os.path.join(PKG_DIR, "kotlin", "com", "avago", "core", "data", "v2", "V2GeneratedSchema.kt")

ROW = re.compile(
    r"^\s+\('(?P<name>[a-z0-9_]+)', '(?P<stream>account|global)', (?P<per_user>true|false), "
    r"ARRAY\[(?P<keys>[^\]]*)\]::text\[\], (?P<mobile>NULL|'[a-z0-9_]+'), '(?P<source>[a-z0-9_.]+)', "
    r"(?P<rank>\d+), (?P<schema>\d+)\),?\s*$"
)


def git_show(svc: str, ref: str, path: str) -> bytes:
    return subprocess.check_output(["git", "-C", svc, "show", f"{ref}:{path}"])


def tables_in(sql: str) -> list[str]:
    return re.findall(r"^CREATE TABLE IF NOT EXISTS (\w+) \(", sql, flags=re.M)


def body_sha(sql: str) -> str:
    m = re.search(r"Body sha256: ([0-9a-f]{64})", sql)
    if not m:
        sys.exit("generated SQL has no 'Body sha256' header")
    return m.group(1)


def parse_manifest(seed: str) -> list[dict]:
    start = seed.index("INSERT INTO platform.sync_projections")
    rows = []
    for line in seed[start:].splitlines()[3:]:
        if line.strip().startswith("ON CONFLICT") or line.strip() == ";":
            break
        m = ROW.match(line)
        if not m:
            if line.strip():
                sys.exit(f"unparsed manifest row: {line!r}")
            continue
        keys = [k.strip().strip("'") for k in m.group("keys").split(",") if k.strip()]
        mobile = None if m.group("mobile") == "NULL" else m.group("mobile").strip("'")
        rows.append({
            "name": m.group("name"),
            "stream": m.group("stream"),
            "per_user": m.group("per_user") == "true",
            "keys": keys,
            "mobile": mobile,
            "rank": int(m.group("rank")),
            "schema": int(m.group("schema")),
        })
    if not rows:
        sys.exit("no projections parsed")
    return rows


def write_bytes(path: str, data: bytes) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--svc", default=os.path.join("..", "AvagoSvc"))
    ap.add_argument("--ref", default="origin/schema-v2")
    args = ap.parse_args()

    rev = subprocess.check_output(["git", "-C", args.svc, "rev-parse", args.ref]).decode().strip()
    business_raw = git_show(args.svc, args.ref, "schema/contract/out/sqlite/business.sql")
    chat_raw = git_show(args.svc, args.ref, "schema/contract/out/sqlite/chat.sql")
    seed = git_show(args.svc, args.ref, "DBSetup/v2/seed/000_sync_projections.sql").decode("utf-8")
    # Normalise to LF so the hash does not depend on the checkout's autocrlf setting.
    business_raw = business_raw.replace(b"\r\n", b"\n")
    chat_raw = chat_raw.replace(b"\r\n", b"\n")
    business = business_raw.decode("utf-8")
    chat = chat_raw.decode("utf-8")

    business_tables = set(tables_in(business))
    chat_tables = set(tables_in(chat))
    rows = parse_manifest(seed)

    lines = []
    for r in sorted(rows, key=lambda r: (r["rank"], r["name"])):
        db = "null"
        if r["mobile"] in business_tables:
            db = "V2StoreKind.BUSINESS"
        elif r["mobile"] in chat_tables:
            db = "V2StoreKind.CHAT"
        elif r["mobile"] is not None:
            sys.exit(f"projection {r['name']} maps to unknown mobile table {r['mobile']}")
        keys = ", ".join(f'"{k}"' for k in r["keys"])
        mobile = f'"{r["mobile"]}"' if r["mobile"] else "null"
        stream = "V2Stream.ACCOUNT" if r["stream"] == "account" else "V2Stream.GLOBAL"
        lines.append(
            f'        V2Projection("{r["name"]}", {stream}, perUser = {"true" if r["per_user"] else "false"}, '
            f'keyColumns = listOf({keys}), mobileTable = {mobile}, store = {db}, '
            f'applyRank = {r["rank"]}, schemaVersion = {r["schema"]}),'
        )

    business_sha = hashlib.sha256(business_raw).hexdigest()
    chat_sha = hashlib.sha256(chat_raw).hexdigest()
    out = f"""// @generated by scripts/gen_v2_contract.py -- DO NOT EDIT.
// Source: AvagoSvc {args.ref} @ {rev}
//   schema/contract/out/sqlite/business.sql (body sha256 {body_sha(business)})
//   schema/contract/out/sqlite/chat.sql (body sha256 {body_sha(chat)})
//   DBSetup/v2/seed/000_sync_projections.sql
// Decision L29: iOS and Android apply the same generated SQLite file (copied to
// core/data/src/main/resources/com/avago/core/data/v2/).
@file:Suppress("MaxLineLength", "LargeClass")

package com.avago.core.data.v2

object V2GeneratedSchema {{
    const val SOURCE_REVISION = "{rev}"
    const val BUSINESS_BODY_SHA256 = "{body_sha(business)}"
    const val CHAT_BODY_SHA256 = "{body_sha(chat)}"

    /** SHA-256 of the LF-normalised resource files; [V2Database] refuses a mismatching copy. */
    const val BUSINESS_FILE_SHA256 = "{business_sha}"
    const val CHAT_FILE_SHA256 = "{chat_sha}"

    const val BUSINESS_SQL_RESOURCE = "/com/avago/core/data/v2/business.sql"
    const val CHAT_SQL_RESOURCE = "/com/avago/core/data/v2/chat.sql"

    /** platform.sync_projections, ordered by (apply_rank, entity_type). */
    val projections: List<V2Projection> = listOf(
{chr(10).join(lines)}
    )

    val projectionsByType: Map<String, V2Projection> = projections.associateBy {{ it.entityType }}
}}
"""
    write_bytes(os.path.join(RES_DIR, "business.sql"), business_raw)
    write_bytes(os.path.join(RES_DIR, "chat.sql"), chat_raw)
    write_bytes(KT_OUT, out.encode("utf-8"))
    print(f"wrote {KT_OUT} + resources: {len(rows)} projections, "
          f"{len(business_tables)} business + {len(chat_tables)} chat tables @ {rev[:9]}")


if __name__ == "__main__":
    main()
