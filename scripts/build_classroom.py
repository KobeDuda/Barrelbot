"""Build against the exact patched Paper server and libraries from a 26.3 installation."""
import argparse
import os
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("server", type=Path, help="Paper 26.3 server directory after its first startup")
parser.add_argument("--plugins", type=Path, help="WorldGuard and WorldEdit or FAWE jar directory")
args = parser.parse_args()
jars = [*args.server.glob("versions/26.3/paper-26.3.jar"), *args.server.glob("libraries/**/*.jar")]
plugins = args.plugins or args.server / "plugins"
for pattern in ("worldguard-bukkit*.jar", "worldedit-bukkit*.jar", "FastAsyncWorldEdit*.jar"):
    jars.extend(plugins.glob(pattern))
if not any(p.name == "paper-26.3.jar" for p in jars):
    parser.error("Patched Paper 26.3 jar missing")
src = root / "classroom/src/main"
target = root / "classroom/target"
target.mkdir(parents=True, exist_ok=True)
argfile = target / "compile.args"
def quote(path):
    return '"' + str(path).replace(chr(92), "/") + '"'
argfile.write_text("--release 25 -encoding UTF-8 -proc:none -classpath " +
                   quote(os.pathsep.join(str(j.resolve()) for j in jars)) +
                   " -d " + quote(target) + "\n" +
                   "\n".join(quote(p) for p in (src / "java").rglob("*.java")), encoding="utf8")
subprocess.run(["javac", "@" + str(argfile)], check=True)
jar = root / "dist/BarrelbotClassroom-26.3.jar"
jar.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as archive:
    for folder in (target, src / "resources"):
        for path in folder.rglob("*"):
            if path.is_file() and path != argfile:
                archive.write(path, path.relative_to(folder).as_posix())
    archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\npaperweight-mappings-namespace: mojang\r\n\r\n")
print(jar)
