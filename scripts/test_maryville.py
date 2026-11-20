"""Exercise the classroom workflows on a temporary copy of Maryville with Paper 26.3."""
import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import threading
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("paper", type=Path, help="Initialized Paper 26.3 directory containing paper.jar and eula.txt")
parser.add_argument("backup", type=Path, help="2026 archive containing Maryville reset-day1-2026 and regions-barrelbot-26.yml")
parser.add_argument("--plugins", type=Path, help="WorldGuard and WorldEdit/FAWE jar directory")
args = parser.parse_args()
server, archive = args.paper.resolve(), args.backup.resolve()
plugins = (args.plugins or server / "plugins").resolve()
os.chdir(ROOT)
subprocess.run([sys.executable, "scripts/build_classroom.py", str(server), "--plugins", str(plugins)], check=True)
test_root = ROOT / ".paper-test"
test_root.mkdir(exist_ok=True)
with tempfile.TemporaryDirectory(prefix="integration-", dir=test_root) as tmp:
    d = Path(tmp)
    for name in ("libraries", "versions", "cache"):
        shutil.copytree(server / name, d / name)
    for name in ("paper.jar", "eula.txt"):
        shutil.copyfile(server / name, d / name)
    world = d / "Maryville"
    world.mkdir()
    source = archive / "Maryville reset-day1-2026"
    for name in ("entities", "region", "data", "poi"):
        shutil.copytree(source / name, world / name)
    shutil.copyfile(source / "level.dat", world / "level.dat")
    subprocess.run([sys.executable, "scripts/package_release.py"], check=True)
    version = (ROOT / "VERSION").read_text().strip()
    (world / "datapacks").mkdir()
    shutil.copyfile(ROOT / f"dist/barrelbot_data_pack_v{version}.zip", world / "datapacks/barrelbot.zip")
    wg = d / "plugins/WorldGuard/worlds/Maryville"
    wg.mkdir(parents=True)
    shutil.copyfile(archive / "regions-barrelbot-26.yml", wg / "regions.yml")
    for pattern in ("worldguard-bukkit*.jar", "FastAsyncWorldEdit*.jar", "worldedit-bukkit*.jar"):
        for jar in plugins.glob(pattern):
            shutil.copyfile(jar, d / "plugins" / jar.name)
    target = ROOT / "classroom/target"
    javac = (target / "compile.args").read_text(encoding="utf8").splitlines()[0]
    javac = javac.replace('-classpath "', '-classpath "' + target.as_posix() + os.pathsep)
    javac = javac.replace('-d "' + target.as_posix() + '"', '-d "' + d.as_posix() + '"')
    src = ROOT / "classroom/src/test/java/org/whimc/barrelbot/Smoke.java"
    (d / "compile.args").write_text(javac + '\n"' + src.as_posix() + '"', encoding="utf8")
    subprocess.run(["javac", "@" + str(d / "compile.args")], check=True)
    with zipfile.ZipFile(ROOT / "dist/BarrelbotClassroom-26.3.jar") as release, zipfile.ZipFile(
        d / "plugins/BarrelbotClassroom.jar", "w", zipfile.ZIP_DEFLATED
    ) as test:
        for name in release.namelist():
            data = release.read(name)
            if name == "plugin.yml":
                data = data.replace(b"main: org.whimc.barrelbot.Classroom", b"main: org.whimc.barrelbot.Smoke")
            test.writestr(name, data)
        test.write(d / "org/whimc/barrelbot/Smoke.class", "org/whimc/barrelbot/Smoke.class")
    (d / "server.properties").write_text("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-name=Maryville\nview-distance=2\nsimulation-distance=3\n")
    passed = False
    with subprocess.Popen(["java", "-Xmx3G", "-Dbarrelbot.isolatedTest=true", "-jar", "paper.jar", "--nogui"],
                          cwd=d, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          text=True, errors="replace") as proc:
        limit = threading.Timer(480, proc.kill)
        limit.start()
        try:
            for line in proc.stdout:
                if "SMOKE" in line or "BASELINE" in line or "IMPORT TARGETS" in line:
                    print(line.rstrip(), flush=True)
                if "SMOKE_PASSED" in line or "SMOKE_FAILED" in line:
                    passed = "SMOKE_PASSED" in line
                    proc.stdin.write("stop\n")
                    proc.stdin.flush()
            if proc.wait() or not passed:
                lines = (d / "logs/latest.log").read_text(encoding="utf8", errors="replace").splitlines()
                sys.exit("Paper integration test failed\n" + "\n".join(lines[-100:]))
        finally:
            limit.cancel()
