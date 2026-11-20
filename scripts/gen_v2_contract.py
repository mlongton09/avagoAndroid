#!/usr/bin/env python3
"""Generate the Android schema-v2 contract inputs from AvagoSvc.

Outputs (do not hand-edit; rerun this script when the contract changes):

    core/data/src/main/resources/com/avago/core/data/v2/business.sql   (verbatim copy)
    core/data/src/main/resources/com/avago/core/data/v2/chat.sql       (verbatim copy)
    core/data/src/main/kotlin/com/avago/core/data/v2/V2GeneratedSchema.kt
    core/data/src/main/kotlin/com/avago/core/data/v2/commands/V2GeneratedCommands.kt
    core/data/src/main/kotlin/com/avago/core/data/v2/V2GeneratedPermissions.kt

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
    schema/contract/out/commands/catalog.json   (the mobile command catalog, P3-14)
    src/v2/contract/loaders.rs                  (history windows, sync-wire.md "History windows")
    schema/fixtures/reference/permission_keys.csv (the permission keys a permission set can hold)

V2GeneratedCommands.kt holds one typed payload class per catalog command (plus the nested line
structs and enums). Each class knows its operation, entity and concurrency rule and encodes the
wire payload: creates/actions/deletes are flat objects, updates use `{"id", "set", "clear"}`
(sync-wire.md "Catalog"), so a nullable field is cleared through the typed `clear` set rather
than by sending null.

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
CMD_OUT = os.path.join(
    PKG_DIR, "kotlin", "com", "avago", "core", "data", "v2", "commands", "V2GeneratedCommands.kt"
)
CATALOG_PATH = "schema/contract/out/commands/catalog.json"
LOADERS_PATH = "src/v2/contract/loaders.rs"
PERMISSIONS_PATH = "schema/fixtures/reference/permission_keys.csv"
PERM_OUT = os.path.join(PKG_DIR, "kotlin", "com", "avago", "core", "data", "v2", "V2GeneratedPermissions.kt")

# Catalog scalar type -> (Kotlin type, wire note). Decimals are BigDecimal (sent as plain strings),
# instants java.time.Instant (RFC 3339), dates java.time.LocalDate, jsonb a JsonElement.
SCALARS = {
    "uuid": "String", "text": "String", "code": "String", "currency": "String",
    "country": "String", "timezone": "String", "email": "String", "locale": "String",
    "qty": "BigDecimal", "decimal": "BigDecimal", "rate_decimal": "BigDecimal",
    "int": "Int", "smallint": "Int", "money_minor": "Long", "bool": "Boolean",
    "instant": "Instant", "date": "LocalDate", "jsonb": "JsonElement",
}

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


LOADER = re.compile(
    r'Loader \{\s*projection: "(?P<name>[a-z0-9_]+)",.*?history_days: (?:None|Some\((?P<days>\d+)\)),'
    r'.*?sql: "(?P<sql>(?:[^"\\]|\\.)*)",\s*\}',
    re.S,
)


def parse_windows(loaders: str, business: str) -> dict[str, tuple[int, str]]:
    """Windowed projections (sync-wire.md "History windows"): name -> (history_days, mobile end column).

    The range column comes from the loader's window predicate (`upper_inf(t."<range>")`); its mobile
    end column (`<range>_end`) must exist in the generated table.
    """
    windows = {}
    for m in LOADER.finditer(loaders):
        if m.group("days") is None:
            continue
        rng = re.search(r'upper_inf\(t\.\\"([a-z0-9_]+)\\"\)', m.group("sql"))
        if not rng:
            sys.exit(f"windowed loader {m.group('name')} has no upper_inf(range) predicate")
        end = rng.group(1) + "_end"
        table = re.search(rf"^CREATE TABLE IF NOT EXISTS {m.group('name')} \((.*?)^\) STRICT", business, re.M | re.S)
        if not table or not re.search(rf"^\s+{end} INTEGER,?$", table.group(1), re.M):
            sys.exit(f"windowed projection {m.group('name')}: mobile column {end} not found")
        windows[m.group("name")] = (int(m.group("days")), end)
    return windows


def camel(name: str) -> str:
    head, *rest = name.split("_")
    return head + "".join(p[:1].upper() + p[1:] for p in rest)


def pascal(name: str) -> str:
    return "".join(p[:1].upper() + p[1:] for p in name.split("_"))


KT_KEYWORDS = {"in", "is", "as", "fun", "val", "var", "object", "when", "class", "interface", "typealias"}


def kt_name(name: str) -> str:
    n = camel(name)
    return f"`{n}`" if n in KT_KEYWORDS else n


def kdoc(text: str | None, indent: str) -> list[str]:
    if not text:
        return []
    text = " ".join(text.split()).replace("*/", "* /")
    return [f"{indent}/** {text} */"]


def kt_type(t: str, enums: dict) -> str:
    if t.startswith("[") and t.endswith("]"):
        return f"List<{kt_type(t[1:-1], enums)}>"
    if t.startswith("enum:"):
        name = pascal(t[5:])
        if name not in enums:
            sys.exit(f"unknown enum {t}")
        return name
    if t in SCALARS:
        return SCALARS[t]
    return t  # a struct


def prop_type(name: str, prop: dict, catalog: dict) -> str:
    """Catalog-style type of one JSON-schema property of a nested struct."""
    if "allOf" in prop and len(prop["allOf"]) == 1:
        merged = {**prop["allOf"][0], **{k: v for k, v in prop.items() if k != "allOf"}}
        return prop_type(name, merged, catalog)
    ref = prop.get("$ref")
    if ref:
        kind, target = ref.split("/")[-2:]
        if kind == "enums":
            if target not in catalog["enums"]:
                sys.exit(f"unknown enum {ref}")
            return "enum:" + re.sub(r"(?<!^)(?=[A-Z])", "_", target).lower()
        if kind == "types":
            return target
        sys.exit(f"unsupported $ref {ref}")
    t = prop.get("type")
    fmt = prop.get("format")
    if t == "array":
        return "[" + prop_type(name, prop["items"], catalog) + "]"
    if t == "string":
        if fmt == "uuid":
            return "uuid"
        if fmt == "date-time":
            return "instant"
        if fmt == "date":
            return "date"
        if prop.get("pattern", "").startswith("^-?[0-9]"):
            return "decimal"
        return "text"  # free text, codes, currency/country/locale codes, inline string enums, emails
    if t == "integer":
        return "money_minor" if fmt == "int64" else "int"
    if t == "boolean":
        return "bool"
    if not prop:
        return "jsonb"
    sys.exit(f"unsupported struct property {name}: {prop}")


def struct_fields(schema: dict, catalog: dict) -> list[dict]:
    """Catalog-style fields of a nested struct from its JSON schema."""
    required = set(schema.get("required", []))
    return [
        {"name": name, "type": prop_type(name, prop, catalog), "required": name in required,
         "doc": prop.get("description")}
        for name, prop in schema["properties"].items()
    ]


def struct_name(t: str, catalog: dict) -> str | None:
    inner = t[1:-1] if t.startswith("[") and t.endswith("]") else t
    schema = catalog["types"].get(inner)
    return inner if schema is not None and "properties" in schema else None


def used_struct_names(catalog: dict) -> list[str]:
    """Structs referenced by command fields, transitively through nested structs."""
    pending = [n for c in catalog["commands"] for f in c["fields"] if (n := struct_name(f["type"], catalog))]
    seen: set[str] = set()
    while pending:
        n = pending.pop()
        if n in seen:
            continue
        seen.add(n)
        pending += [m for f in struct_fields(catalog["types"][n], catalog) if (m := struct_name(f["type"], catalog))]
    return sorted(seen)


def param_lines(fields: list[dict], enums: dict, force_optional: bool = False) -> list[str]:
    lines = []
    for f in fields:
        t = kt_type(f["type"], enums)
        lines += kdoc(f.get("doc"), "    ")
        if f.get("required") and not force_optional:
            lines.append(f"    val {kt_name(f['name'])}: {t},")
        else:
            lines.append(f"    val {kt_name(f['name'])}: {t}? = null,")
    return lines


def pairs(fields: list[dict]) -> str:
    return ", ".join(f'"{f["name"]}" to {kt_name(f["name"])}' for f in fields)


def commands_kotlin(catalog: dict, source: str) -> str:
    enums = {name: values for name, values in catalog["enums"].items()}
    used_structs = used_struct_names(catalog)
    out = [
        "// @generated by scripts/gen_v2_contract.py -- DO NOT EDIT.",
        f"// Source: {source}",
        "// Mobile command catalog (sync-wire.md \"Catalog\"): one typed payload per command.",
        '@file:Suppress("MaxLineLength", "LargeClass", "LongParameterList", "TooManyFunctions")',
        "",
        "package com.avago.core.data.v2.commands",
        "",
        "import kotlinx.serialization.json.JsonArray",
        "import kotlinx.serialization.json.JsonElement",
        "import kotlinx.serialization.json.JsonObject",
        "import kotlinx.serialization.json.JsonPrimitive",
        "import java.math.BigDecimal",
        "import java.time.Instant",
        "import java.time.LocalDate",
        "",
        f"const val V2_COMMAND_CATALOG_VERSION: Int = {catalog['catalog_version']}",
        "",
        "/** Every operation in the catalog (what `capabilities.commands` lists). */",
        "val V2_CATALOG_OPERATIONS: Set<String> = setOf(",
    ]
    out += [f'    "{c["name"]}",' for c in catalog["commands"]]
    out += [")", ""]

    for name in sorted(enums):
        out.append(f"enum class {name}(override val wire: String) : V2WireEnum {{")
        for v in enums[name]:
            out.append(f'    {v.upper()}("{v}"),')
        out += ["}", ""]

    for name in used_structs:
        schema = catalog["types"][name]
        fields = struct_fields(schema, catalog)
        out += kdoc(schema.get("description"), "")
        out.append(f"data class {name}(")
        out += param_lines(fields, enums)
        out.append(") : V2PayloadStruct {")
        out.append(f"    override fun toJson(): JsonObject = V2PayloadJson.objectOf({pairs(fields)})")
        out += ["}", ""]

    for c in sorted(catalog["commands"], key=lambda c: c["name"]):
        cls = c["payload"]
        fields = c["fields"]
        doc = f"`{c['name']}` ({c['kind']} on {c['entity']}). {c.get('doc') or ''}"
        if c.get("permissions"):
            doc += " Permission: " + " or ".join(c["permissions"]) + "."
        out += kdoc(doc, "")
        out.append(f"data class {cls}(")
        if c["kind"] == "update":
            clearable = c.get("clearable", [])
            out.append(f"    val {c['key']}: String,")
            out += param_lines(fields, enums, force_optional=True)
            if clearable:
                out.append("    /** Nullable fields to set to NULL (a field cannot be both set and cleared). */")
                out.append("    val clear: Set<Clear> = emptySet(),")
            out.append(") : V2CommandPayload {")
            if clearable:
                out.append("    enum class Clear(override val wire: String) : V2WireEnum {")
                for f in clearable:
                    out.append(f'        {f.upper()}("{f}"),')
                out += ["    }", ""]
            out.append("    override fun toJson(): JsonObject {")
            out.append(f"        val set = V2PayloadJson.objectOf({pairs(fields)})")
            if clearable:
                out.append("        val clearSet = clear.mapTo(mutableSetOf()) { it.wire }")
                out.append("        require(set.keys.none { it in clearSet }) { \"a field cannot be both set and cleared\" }")
                out.append("        require(set.isNotEmpty() || clear.isNotEmpty()) { \"an update must change something\" }")
                out.append(f'        return JsonObject(mapOf("{c["key"]}" to JsonPrimitive({c["key"]}), "set" to set, '
                           '"clear" to JsonArray(clearSet.sorted().map(::JsonPrimitive))))')
            else:
                out.append("        require(set.isNotEmpty()) { \"an update must change something\" }")
                out.append(f'        return JsonObject(mapOf("{c["key"]}" to JsonPrimitive({c["key"]}), "set" to set, '
                           '"clear" to JsonArray(emptyList())))')
            out.append("    }")
        else:
            out += param_lines(fields, enums)
            out.append(") : V2CommandPayload {")
            out.append(f"    override fun toJson(): JsonObject = V2PayloadJson.objectOf({pairs(fields)})")
        if c["key"] is None and (c["kind"] == "update" or c["concurrency"] == "version"):
            sys.exit(f"{c['name']}: a keyless command cannot be an update or carry a base version")
        key = kt_name(c["key"]) if c["key"] is not None else "null"
        out += [
            "",
            f'    override val commandName: String get() = "{c["name"]}"',
            f'    override val commandEntityType: String get() = "{c["entity"]}"',
            f"    override val commandEntityKey: String? get() = {key}",
            f"    override val commandKind: V2CommandKind get() = V2CommandKind.{c['kind'].upper()}",
            f"    override val requiresBaseVersion: Boolean get() = {'true' if c['concurrency'] == 'version' else 'false'}",
            "}",
            "",
        ]
    return "\n".join(out).rstrip() + "\n"


def kt_string(text: str) -> str:
    return '"' + text.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'


def permissions_kotlin(csv_text: str, source: str) -> str:
    import csv
    import io

    rows = sorted(csv.DictReader(io.StringIO(csv_text)), key=lambda r: r["permission_key"])
    out = [
        "// @generated by scripts/gen_v2_contract.py -- DO NOT EDIT.",
        f"// Source: {source}",
        '@file:Suppress("MaxLineLength", "LargeClass")',
        "",
        "package com.avago.core.data.v2",
        "",
        "/** A permission key a permission set can hold (the server's permission_keys reference). */",
        "data class V2PermissionKey(val key: String, val area: String, val description: String)",
        "",
        "object V2GeneratedPermissions {",
        "    val keys: List<V2PermissionKey> = listOf(",
    ]
    for r in rows:
        out.append(
            f"        V2PermissionKey({kt_string(r['permission_key'])}, {kt_string(r['area'])}, "
            f"{kt_string(r['description'])}),"
        )
    out += ["    )", "", "    val byKey: Map<String, V2PermissionKey> = keys.associateBy { it.key }", "}"]
    return "\n".join(out) + "\n"


def write_bytes(path: str, data: bytes) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--svc", default=os.path.join("..", "AvagoSvc"))
    ap.add_argument("--ref", default="origin/schema-v2")
    ap.add_argument(
        "--catalog-ref",
        help="git ref to read the command catalog from (default: --ref)",
    )
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
    windows = parse_windows(git_show(args.svc, args.ref, LOADERS_PATH).decode("utf-8"), business)

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
        window = ""
        if r["name"] in windows and r["mobile"] is not None:
            days, end = windows[r["name"]]
            window = f', historyWindow = V2HistoryWindow(days = {days}, endColumn = "{end}")'
        lines.append(
            f'        V2Projection("{r["name"]}", {stream}, perUser = {"true" if r["per_user"] else "false"}, '
            f'keyColumns = listOf({keys}), mobileTable = {mobile}, store = {db}, '
            f'applyRank = {r["rank"]}, schemaVersion = {r["schema"]}{window}),'
        )

    business_sha = hashlib.sha256(business_raw).hexdigest()
    chat_sha = hashlib.sha256(chat_raw).hexdigest()
    out = f"""// @generated by scripts/gen_v2_contract.py -- DO NOT EDIT.
// Source: AvagoSvc {args.ref} @ {rev}
//   schema/contract/out/sqlite/business.sql (body sha256 {body_sha(business)})
//   schema/contract/out/sqlite/chat.sql (body sha256 {body_sha(chat)})
//   DBSetup/v2/seed/000_sync_projections.sql
//   {LOADERS_PATH} (history windows)
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

    import json

    cat_ref = args.catalog_ref or args.ref
    cat_rev = subprocess.check_output(["git", "-C", args.svc, "rev-parse", cat_ref]).decode().strip()
    catalog = json.loads(git_show(args.svc, cat_ref, CATALOG_PATH).decode("utf-8"))
    commands = commands_kotlin(catalog, f"AvagoSvc {cat_ref} @ {cat_rev} ({CATALOG_PATH})")
    write_bytes(CMD_OUT, commands.encode("utf-8"))
    print(f"wrote {CMD_OUT}: {len(catalog['commands'])} commands @ {cat_rev[:9]}")
    perm_csv = git_show(args.svc, cat_ref, PERMISSIONS_PATH).decode("utf-8").replace("\r\n", "\n")
    write_bytes(PERM_OUT, permissions_kotlin(perm_csv, f"AvagoSvc {cat_ref} @ {cat_rev} ({PERMISSIONS_PATH})").encode("utf-8"))
    print(f"wrote {PERM_OUT}")
    print(f"wrote {KT_OUT} + resources: {len(rows)} projections, "
          f"{len(business_tables)} business + {len(chat_tables)} chat tables @ {rev[:9]}")


if __name__ == "__main__":
    main()
