#!/usr/bin/env bash
# waitci.sh <sha-prefix>: waits for the "build" workflow run of that commit and prints the first failing job
# (name, conclusion, job id) or "release <result>" once the test-build release job is done.
# A [release] commit: use waitstable.sh instead. Needs only curl and python3 (public GitHub API).
SHA=$1
BRANCH=${BRANCH:-claude/minecraft-dantes-inferno-mod-ukodio}
REPO=${REPO:-The-small-things/hellcraft}
JOB=${JOB:-release}
until RUN=$(curl -s "https://api.github.com/repos/$REPO/actions/runs?branch=$BRANCH&per_page=10" | python3 -c "
import json,sys
for r in json.load(sys.stdin)['workflow_runs']:
  if r['name']=='build' and r['head_sha'].startswith('$SHA'): print(r['id']); break
") && [ -n "$RUN" ] && curl -s "https://api.github.com/repos/$REPO/actions/runs/$RUN/jobs" | python3 -c "
import json,sys
js={j['name']:j for j in json.load(sys.stdin)['jobs']}
for n in ('build-26','smoke-test-26','client-test-26'):
  j=js.get(n)
  if j and j['conclusion'] in ('failure','cancelled'): print(n,j['conclusion'],j['id']); sys.exit(0)
r=js.get('$JOB')
if r and r['status']=='completed': print('$JOB',r['conclusion']); sys.exit(0)
sys.exit(1)"; do sleep 30; done
