#!/usr/bin/env python3
"""testshard.py <n>: prints the client test classes CI machine <n> runs, comma-separated (tools/dev/clienttest.sh uses it
for HELLCRAFT_SHARD). The shards are the lines of tools/dev/test-shards.txt; the one marked * also runs every class in
the test manifest (versions/26.3/src/gametest/resources/fabric.mod.json) that no line names, so a new test is never
left out. `testshard.py check` checks the table against the manifest (every class runs once, none is unknown)."""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TABLE = os.path.join(HERE, "test-shards.txt")
MANIFEST = os.path.join(HERE, "..", "..", "versions", "26.3", "src", "gametest", "resources", "fabric.mod.json")


def shards():
    lines = {}
    for raw in open(TABLE):
        words = raw.split("#", 1)[0].split()
        if words:
            lines[words[0]] = words[1:]
    return lines


def manifest():
    return [c.rsplit(".", 1)[-1] for c in json.load(open(MANIFEST))["entrypoints"]["fabric-client-gametest"]]


def tests(shard):
    lines = shards()
    named = [c for cls in lines.values() for c in cls if c != "*"]
    every = manifest()
    mine = [c for c in lines.get(shard, []) if c != "*"]
    if "*" in lines.get(shard, []):
        mine += [c for c in every if c not in named]
    return mine


def check():
    lines = shards()
    every = manifest()
    named = [c for cls in lines.values() for c in cls if c != "*"]
    problems = [c + " is not a test class" for c in named if c not in every]
    problems += [c + " is in two shards" for c in set(named) if named.count(c) > 1]
    if sum("*" in cls for cls in lines.values()) != 1:
        problems.append("exactly one shard must be marked *")
    runs = [c for s in lines for c in tests(s)]
    problems += [c + " runs in no shard" for c in every if c not in runs]
    return problems


if __name__ == "__main__":
    if sys.argv[1:] == ["check"]:
        problems = check()
        print("\n".join(problems) if problems else "Test shards OK: " + "; ".join(s + ": " + ",".join(tests(s)) for s in shards()))
        sys.exit(1 if problems else 0)
    mine = tests(sys.argv[1])
    if not mine or check():
        sys.exit("test shard " + sys.argv[1] + ": " + ("; ".join(check()) or "no tests"))
    print(",".join(mine))
