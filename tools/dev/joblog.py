"""joblog.py <saved-log> <out>: strips the timestamps from a GitHub Actions job log (raw or the MCP tool's JSON)."""
import json, re, sys

raw = open(sys.argv[1]).read()
try:
    s = json.loads(raw).get("logs_content", raw)
except Exception:
    s = raw
lines = [re.sub(r"^\d{4}-\d\d-\d\dT[\d:.]+Z ", "", l) for l in s.split("\n")]
open(sys.argv[2], "w").write("\n".join(lines))
print(len(lines), "lines")
