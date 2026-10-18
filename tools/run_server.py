#!/usr/bin/env python3
"""Run the built GregTech port on an installed NeoForge dedicated server."""

from __future__ import annotations

import os
import shutil
import subprocess
import sys
from pathlib import Path


PROJECT = Path(__file__).resolve().parents[1]
RUN_DIR = PROJECT / "run-server"


def main() -> int:
    built_jars = sorted((PROJECT / "build" / "libs").glob("gregtech-neoforge-*.jar"))
    built_jars = [path for path in built_jars if "sources" not in path.name]
    if not built_jars:
        print("Build the mod first: ./gradlew build", file=sys.stderr)
        return 2

    run_script = RUN_DIR / "run.sh"
    if not run_script.is_file():
        print(
            "NeoForge dedicated server is not installed in run-server.\n"
            "Run: java -jar neoforge-21.1.227-installer.jar --installServer run-server",
            file=sys.stderr,
        )
        return 2

    mods_dir = RUN_DIR / "mods"
    mods_dir.mkdir(exist_ok=True)
    for old_mod in mods_dir.glob("gregtech-neoforge-*.jar"):
        old_mod.unlink()
    shutil.copy2(built_jars[-1], mods_dir / built_jars[-1].name)

    (RUN_DIR / "eula.txt").write_text(
        "# Accepted for the isolated local self-test environment.\neula=true\n",
        encoding="utf-8",
    )
    server_properties = RUN_DIR / "server.properties"
    if not server_properties.exists():
        server_properties.write_text(
            "level-type=minecraft\\:flat\n"
            "generate-structures=false\n"
            "online-mode=false\n"
            "server-port=0\n"
            "spawn-protection=0\n",
            encoding="utf-8",
        )

    environment = os.environ.copy()
    extra_jvm_args = environment.get("GT6_JVM_ARGS", "").strip()
    if extra_jvm_args:
        existing = environment.get("JAVA_TOOL_OPTIONS", "").strip()
        environment["JAVA_TOOL_OPTIONS"] = f"{existing} {extra_jvm_args}".strip()

    print(f"Launching isolated GregTech server from {RUN_DIR}")
    try:
        return subprocess.run(["sh", "run.sh", "nogui"], cwd=RUN_DIR, env=environment).returncode
    except KeyboardInterrupt:
        print("\nServer launch interrupted.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
