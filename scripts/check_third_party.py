#!/usr/bin/env python3
"""Fail when the dependency set and the third-party inventory disagree.

Checks (all must hold):
  * every library and plugin in gradle/libs.versions.toml has a component in
    third_party.json whose `coordinates` / `plugin_ids` cover it at the same
    version;
  * every component's coordinates still exist in the catalog (no stale rows),
    except rows marked "transitive": true (pulled in by a catalogued dependency);
  * required fields are present, `spdx`/`scope` are sane, and
    `license_verified_from` is an http(s) URL;
  * every component is named in THIRD_PARTY_NOTICES.md;
  * build files declare no dependency by literal coordinate outside the catalog.

Standard library only. Run from anywhere; paths resolve against the repo root.
"""
from __future__ import annotations

import json
import re
import sys
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
REQUIRED = (
    "name",
    "upstream_url",
    "version_or_commit",
    "spdx",
    "license_verified_from",
    "files",
    "notices_required",
    "modified",
    "scope",
)
SCOPES = {"runtime", "test", "build"}


def catalog() -> tuple[dict[str, str], dict[str, str]]:
    data = tomllib.loads((ROOT / "gradle/libs.versions.toml").read_text())
    versions = data.get("versions", {})

    def resolve(spec: dict, what: str) -> str:
        version = spec.get("version")
        if isinstance(version, str):
            return version
        if isinstance(version, dict) and "ref" in version:
            ref = version["ref"]
            if ref not in versions:
                raise SystemExit(f"{what}: unknown version ref {ref!r}")
            return versions[ref]
        raise SystemExit(f"{what}: no version / version.ref")

    libs: dict[str, str] = {}
    for alias, spec in data.get("libraries", {}).items():
        if "module" in spec:
            module = spec["module"]
        else:
            module = f"{spec['group']}:{spec['name']}"
        libs[module] = resolve(spec, f"library {alias}")
    plugins: dict[str, str] = {}
    for alias, spec in data.get("plugins", {}).items():
        plugins[spec["id"]] = resolve(spec, f"plugin {alias}")
    return libs, plugins


def main() -> int:
    problems: list[str] = []
    try:
        inventory = json.loads((ROOT / "third_party.json").read_text())
    except FileNotFoundError:
        print("FAIL: third_party.json is missing")
        return 1
    components = inventory.get("components", [])
    notices = (ROOT / "THIRD_PARTY_NOTICES.md").read_text() if (ROOT / "THIRD_PARTY_NOTICES.md").exists() else ""
    if not notices:
        problems.append("THIRD_PARTY_NOTICES.md is missing or empty")

    libs, plugins = catalog()
    covered_libs: dict[str, str] = {}
    covered_plugins: dict[str, str] = {}
    transitive: set[str] = set()  # pulled in by another dependency, not in the catalog

    for comp in components:
        name = comp.get("name", "<unnamed>")
        for field in REQUIRED:
            if field not in comp:
                problems.append(f"{name}: missing field {field!r}")
        if comp.get("scope") not in SCOPES:
            problems.append(f"{name}: scope must be one of {sorted(SCOPES)}")
        if not str(comp.get("spdx", "")).strip():
            problems.append(f"{name}: empty spdx")
        if not re.match(r"https?://", str(comp.get("license_verified_from", ""))):
            problems.append(f"{name}: license_verified_from must be an http(s) URL")
        if not re.match(r"https?://", str(comp.get("upstream_url", ""))):
            problems.append(f"{name}: upstream_url must be an http(s) URL")
        if name not in notices:
            problems.append(f"{name}: not named in THIRD_PARTY_NOTICES.md")
        for coord in comp.get("coordinates", []):
            covered_libs[coord] = comp.get("version_or_commit", "")
            if comp.get("transitive"):
                transitive.add(coord)
        for pid in comp.get("plugin_ids", []):
            covered_plugins[pid] = comp.get("version_or_commit", "")

    for module, version in sorted(libs.items()):
        if module not in covered_libs:
            problems.append(f"dependency {module}:{version} has no third_party.json component")
        elif covered_libs[module] != version:
            problems.append(
                f"dependency {module}: catalog says {version}, third_party.json says {covered_libs[module]}"
            )
    for pid, version in sorted(plugins.items()):
        if pid not in covered_plugins:
            problems.append(f"plugin {pid}:{version} has no third_party.json component")
        elif covered_plugins[pid] != version:
            problems.append(
                f"plugin {pid}: catalog says {version}, third_party.json says {covered_plugins[pid]}"
            )
    for module in sorted(set(covered_libs) - set(libs) - transitive):
        problems.append(f"third_party.json lists {module} but the catalog does not (stale entry)")
    for pid in sorted(set(covered_plugins) - set(plugins)):
        problems.append(f"third_party.json lists plugin {pid} but the catalog does not (stale entry)")

    literal = re.compile(
        r"""^\s*(?:implementation|api|ksp|kapt|compileOnly|runtimeOnly|testImplementation|"""
        r"""androidTestImplementation|debugImplementation|testRuntimeOnly)\(\s*["'][^"']+:[^"']+:[^"']+["']"""
    )
    for build_file in list(ROOT.glob("*.gradle.kts")) + list(ROOT.glob("*/*.gradle.kts")):
        for lineno, line in enumerate(build_file.read_text().splitlines(), 1):
            if literal.match(line):
                problems.append(
                    f"{build_file.relative_to(ROOT)}:{lineno}: dependency declared by literal "
                    "coordinate; add it to gradle/libs.versions.toml and third_party.json"
                )

    if problems:
        print("FAIL: third-party inventory is out of sync")
        for problem in problems:
            print(f"  - {problem}")
        return 1
    print(
        f"PASS: {len(libs)} libraries and {len(plugins)} plugins covered by "
        f"{len(components)} inventory components"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
