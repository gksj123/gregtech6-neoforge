#!/usr/bin/env python3
"""Launch the supplied Minecraft 1.21.1 + NeoForge instance in an isolated directory."""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
from pathlib import Path


PROJECT = Path(__file__).resolve().parents[1]
MINECRAFT_HOME = Path(
    os.environ.get("GT6_MINECRAFT_HOME", "/Users/nanhy/Desktop/minecraft/.minecraft")
).expanduser()
VERSION_ID = os.environ.get("GT6_MINECRAFT_VERSION", "create")
VERSION_DIR = MINECRAFT_HOME / "versions" / VERSION_ID
LIBRARIES = MINECRAFT_HOME / "libraries"
RUN_DIR = PROJECT / "run-client"


def artifact_path(library: dict) -> str | None:
    downloads = library.get("downloads", {})
    artifact = downloads.get("artifact")
    if artifact and artifact.get("path"):
        return artifact["path"]
    for value in downloads.values():
        if isinstance(value, dict) and value.get("path"):
            return value["path"]
    return None


def main() -> int:
    profile = json.loads((VERSION_DIR / f"{VERSION_ID}.json").read_text())
    native_dir = VERSION_DIR / "natives-macos-arm64"
    if not native_dir.is_dir():
        print(f"Missing native directory: {native_dir}", file=sys.stderr)
        return 2

    jars: list[str] = []
    for library in profile["libraries"]:
        relative = artifact_path(library)
        if relative is None:
            continue
        candidate = LIBRARIES / relative
        if candidate.is_file():
            jars.append(str(candidate))

    built_jars = sorted((PROJECT / "build" / "libs").glob("gregtech-neoforge-*.jar"))
    built_jars = [path for path in built_jars if "sources" not in path.name]
    if not built_jars:
        print("Build the mod first: ./gradlew build", file=sys.stderr)
        return 2

    mods_dir = RUN_DIR / "mods"
    mods_dir.mkdir(parents=True, exist_ok=True)
    for old_mod in mods_dir.glob("gregtech-neoforge-*.jar"):
        old_mod.unlink()
    shutil.copy2(built_jars[-1], mods_dir / built_jars[-1].name)

    module_jars = [
        LIBRARIES / "cpw/mods/bootstraplauncher/2.0.2/bootstraplauncher-2.0.2.jar",
        LIBRARIES / "cpw/mods/securejarhandler/3.0.8/securejarhandler-3.0.8.jar",
        LIBRARIES / "org/ow2/asm/asm-commons/9.8/asm-commons-9.8.jar",
        LIBRARIES / "org/ow2/asm/asm-util/9.8/asm-util-9.8.jar",
        LIBRARIES / "org/ow2/asm/asm-analysis/9.8/asm-analysis-9.8.jar",
        LIBRARIES / "org/ow2/asm/asm-tree/9.8/asm-tree-9.8.jar",
        LIBRARIES / "org/ow2/asm/asm/9.8/asm-9.8.jar",
        LIBRARIES / "net/neoforged/JarJarFileSystems/0.4.1/JarJarFileSystems-0.4.1.jar",
    ]

    separator = os.pathsep
    classpath = separator.join([str(VERSION_DIR / f"{VERSION_ID}.jar"), *jars])
    module_path = separator.join(str(path) for path in module_jars)
    assets = MINECRAFT_HOME / "assets"

    command = [
        "java",
        "-Xdock:name=Minecraft",
        "-XstartOnFirstThread",
        "-Xmx3G",
        f"-Djava.library.path={native_dir}",
        f"-Djna.tmpdir={native_dir}",
        f"-Dorg.lwjgl.system.SharedLibraryExtractPath={native_dir}",
        f"-Dio.netty.native.workdir={native_dir}",
        "-Dminecraft.launcher.brand=Codex",
        "-Dminecraft.launcher.version=local",
        "-Djava.net.preferIPv6Addresses=system",
        f"-DignoreList=client-extra,{VERSION_ID}.jar,{VERSION_ID}.jar",
        f"-DlibraryDirectory={LIBRARIES}",
        "-cp",
        classpath,
        "-p",
        module_path,
        "--add-modules",
        "ALL-MODULE-PATH",
        "--add-opens",
        "java.base/java.util.jar=cpw.mods.securejarhandler",
        "--add-opens",
        "java.base/java.lang.invoke=cpw.mods.securejarhandler",
        "--add-exports",
        "java.base/sun.security.util=cpw.mods.securejarhandler",
        "--add-exports",
        "jdk.naming.dns/com.sun.jndi.dns=java.naming",
        "cpw.mods.bootstraplauncher.BootstrapLauncher",
        "--username",
        "Codex",
        "--version",
        VERSION_ID,
        "--gameDir",
        str(RUN_DIR),
        "--assetsDir",
        str(assets),
        "--assetIndex",
        profile["assetIndex"]["id"],
        "--uuid",
        "00000000000000000000000000000000",
        "--accessToken",
        "0",
        "--clientId",
        "0",
        "--xuid",
        "0",
        "--userType",
        "legacy",
        "--versionType",
        "release",
        "--fml.neoForgeVersion",
        "21.1.227",
        "--fml.fmlVersion",
        "4.0.42",
        "--fml.mcVersion",
        "1.21.1",
        "--fml.neoFormVersion",
        "20240808.144430",
        "--launchTarget",
        "forgeclient",
    ]

    extra_jvm_args = os.environ.get("GT6_JVM_ARGS", "").strip()
    if extra_jvm_args:
        command[1:1] = extra_jvm_args.split()
    quick_play = os.environ.get("GT6_QUICK_PLAY", "").strip()
    if quick_play:
        fml_index = command.index("--fml.neoForgeVersion")
        command[fml_index:fml_index] = ["--quickPlaySingleplayer", quick_play]

    print(f"Launching isolated GregTech client from {RUN_DIR}")
    try:
        return subprocess.run(command, cwd=RUN_DIR).returncode
    except KeyboardInterrupt:
        print("\nClient launch interrupted.", file=sys.stderr)
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
