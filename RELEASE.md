# Build and release Barrelbot for Paper 26.3

Build the classroom JAR with Java 25 and Python 3.10 or later. Supply a Paper 26.3 server folder that has initialized its libraries. WorldGuard and either WorldEdit or FAWE must be available in its plugins directory, or pass a separate directory with the optional plugins argument.

```sh
python scripts/build_classroom.py /path/to/paper --plugins /path/to/plugins
python scripts/package_release.py
```

The three deliverables are in `dist/`. The datapack and resource pack ZIPs have `pack.mcmeta` at the archive root. Edit the migrated trees under `build/` for this release. The original Beet sources under `src/` predate the component migration.

To run the Maryville integration exercise, use the supplied reset-day archive. The runner copies terrain and entities into a fresh temporary server, runs synthetic accounts and setup operations, then removes its test copy.

```sh
python scripts/test_maryville.py /path/to/paper "/path/to/maryville backup/2026" --plugins /path/to/plugins
```

The release workflow uses pinned Paper, WorldGuard and FAWE dependencies. A manual workflow run packages the artifacts. A version tag publishes them with the corresponding file in `release-notes/`. See [CLASSROOM.md](CLASSROOM.md) for installation and use.
