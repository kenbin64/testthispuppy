"""Scores each tool's raw output against the answer key (defects.json), using the rules in METHOD.md.

    python score.py            reads results/raw/, writes results/SCORES.md and results/scores.json

Every credit is printed with the finding that earned it, so the scoring can be checked by hand.
Interpretations, stated rather than hidden:
  - TestThisPuppy summary lines ("practices: 3 issue(s)", the test-the-tests score line) are not findings; they only
    count what is already listed. Per-issue code lines in its report repeat its fix list, so the fix list is used.
  - Code and test defects are matched by file and line only; behaviour defects by file and line or by a running-app
    finding (endpoint and keyword); the dependency defect by package or CVE.
  - Findings that match no defect are reported as "outside the answer key". That is not the same as false: many are
    genuine (real CVEs in the framework version, real untested code, real style issues).
"""
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).parent
RAW = HERE / "results" / "raw"
ZOO = HERE / "bugzoo"


def words(text):
    return " " + re.sub(r"[^a-z0-9]+", " ", str(text).lower()).strip() + " "


def load_defects():
    key = json.loads((HERE / "defects.json").read_text(encoding="utf-8"))["defects"]
    for d in key:
        lines = (ZOO / d["file"]).read_text(encoding="utf-8").splitlines()
        hits = [i + 1 for i, line in enumerate(lines) if d["anchor"] in line]
        assert len(hits) == 1, f"{d['id']}: anchor found {len(hits)} times"
        d["line"] = hits[0]
    return key


def finding(tool, text, file=None, start=None, end=None):
    return {"tool": tool, "text": str(text), "file": file, "start": start, "end": end if end is not None else start}


def same_file(defect_file, tool_file):
    if not tool_file:
        return False
    a, b = defect_file.replace("\\", "/").lower(), str(tool_file).replace("\\", "/").lower()
    return a.endswith(b) or b.endswith(a) or a.split("/")[-1] == b.split("/")[-1] and b.count("/") == 0


def static_match(d, f):
    if not same_file(d["file"], f["file"]) or f["start"] is None:
        return False
    lo, hi = d["line"] - d["span"], d["line"] + d["span"]
    if f["end"] < lo or f["start"] > hi:
        return False
    text = f["text"].lower()
    return any(k.lower() in text for k in d["keywords"])


def running_match(d, f):
    if d["kind"] == "dependency":
        return any(k.lower() in f["text"].lower() for k in d["keywords"])
    if d["kind"] != "behaviour":
        return False
    text = words(f["text"])
    if d.get("endpoint") and words(d["endpoint"]) not in text:
        return False
    return any(words(k).strip() in text for k in d["keywords"])


# ---- readers: one per tool, each returning a list of findings ----

def read_puppy(run):
    out = []
    report = json.loads((HERE / "puppy" / f"report-{run}.json").read_text(encoding="utf-8"))
    for r in report["results"]:
        if r["passed"] or r["kind"] == "code":
            continue
        if r["kind"] == "mutation":
            m = re.match(r"not caught: (\S+):(\d+)", r["name"])
            if m:
                out.append(finding("TestThisPuppy", "mutation not caught " + r["detail"], m.group(1), int(m.group(2))))
            continue
        out.append(finding("TestThisPuppy", f"{r['kind']} {r['name']} {r['detail']}"))
    current = None
    for line in (RAW / f"puppy-{run}-code-fixes.md").read_text(encoding="utf-8").splitlines():
        if line.startswith("## "):
            current = line[3:].strip()
        m = re.match(r"- \*\*line (\d+)\*\*, (.*)", line)
        if m and current:
            out.append(finding("TestThisPuppy", m.group(2), current, int(m.group(1))))
    return out


def read_spotbugs():
    out = []
    for bug in ET.parse(RAW / "spotbugs.xml").getroot().iter("BugInstance"):
        src = bug.find("SourceLine")
        msg = bug.findtext("ShortMessage") or ""
        long_msg = bug.findtext("LongMessage") or ""
        start = int(src.get("start")) if src is not None and src.get("start") else None
        end = int(src.get("end")) if src is not None and src.get("end") else start
        # A method-level bug points at the method; use the bug's own most specific line when it has one.
        lines = [s for s in bug.findall("SourceLine") if s.get("start")]
        if lines:
            start, end = min(int(s.get("start")) for s in lines), max(int(s.get("end") or s.get("start")) for s in lines)
        out.append(finding("SpotBugs", f"{bug.get('type')} {bug.get('category')} {msg} {long_msg}",
                           src.get("sourcepath") if src is not None else None, start, end))
    return out


def read_sonar(label):
    out = []
    issues = json.loads((RAW / f"sonar-{label}-issues.json").read_text(encoding="utf-8-sig"))
    for i in issues.get("issues", []):
        out.append(finding(f"SonarQube ({label})", f"{i['rule']} {i.get('type', '')} {i['message']}",
                           i["component"].split(":", 1)[-1], i.get("line"), (i.get("textRange") or {}).get("endLine", i.get("line"))))
    hot = json.loads((RAW / f"sonar-{label}-hotspots.json").read_text(encoding="utf-8-sig"))
    for h in hot.get("hotspots", []):
        out.append(finding(f"SonarQube ({label})", f"{h['ruleKey']} hotspot {h['message']}", h["component"].split(":", 1)[-1], h.get("line")))
    return out


def read_zap():
    out = []
    data = json.loads((RAW / "zap.json").read_text(encoding="utf-8-sig"))
    for site in data.get("site", []):
        for a in site.get("alerts", []):
            if int(a.get("riskcode", 0)) < 1:
                continue
            inst = " ".join(f"{i.get('uri', '')} {i.get('param', '')} {i.get('evidence', '')}" for i in a.get("instances", []))
            out.append(finding("OWASP ZAP", f"{a.get('name', '')} {a.get('alert', '')} {inst}"))
    return out


def read_osv():
    out = []
    data = json.loads((RAW / "osv.json").read_text(encoding="utf-8-sig"))
    for r in data.get("results", []):
        for p in r.get("packages", []):
            for v in p.get("vulnerabilities", []):
                out.append(finding("OSV-Scanner", f"{p['package']['name']} {p['package']['version']} {v.get('id', '')} {' '.join(v.get('aliases', []))}"))
    return out


TOOLS = {
    "TestThisPuppy (automatic)": lambda: read_puppy("automatic"),
    "TestThisPuppy (with spec)": lambda: read_puppy("spec"),
    "SonarQube (default profile)": lambda: read_sonar("default"),
    "SonarQube (comprehensive profile)": lambda: read_sonar("comprehensive"),
    "SpotBugs + FindSecBugs": read_spotbugs,
    "OWASP ZAP": read_zap,
    "OSV-Scanner": read_osv,
}


def score(defects, findings):
    found, used = {}, set()
    for d in defects:
        for n, f in enumerate(findings):
            if static_match(d, f) or (f["file"] is None and running_match(d, f)):
                found[d["id"]] = f["text"][:160]
                used.add(n)
                break
    outside = len(findings) - len({n for n, f in enumerate(findings) if any(static_match(d, f) or (f['file'] is None and running_match(d, f)) for d in defects)})
    return found, outside


AUDIT = json.loads((HERE / "audit.json").read_text(encoding="utf-8"))["changes"]


def main():
    defects = load_defects()
    results = {}
    for tool, read in TOOLS.items():
        try:
            fs = read()
        except FileNotFoundError as e:
            print(f"{tool}: no results ({e.filename})")
            continue
        found, outside = score(defects, fs)
        audited = set(found)
        for c in AUDIT:
            if c["tool"] == tool and c["change"] == "remove":
                audited.discard(c["defect"])
            elif c["tool"] == tool and c["change"] == "add":
                audited.add(c["defect"])
        results[tool] = {"found": found, "audited": sorted(audited), "findings": len(fs), "outside": outside}
        print(f"\n{tool}: {len(found)} of {len(defects)} defects, {len(fs)} findings, {outside} outside the key")
        for did, why in found.items():
            print(f"  {did}: {why}")
    kinds = ["behaviour", "code", "tests", "dependency"]
    md = ["# Benchmark scores", "", "Bug zoo: 21 planted defects (" + ", ".join(f"{sum(d['kind'] == k for d in defects)} {k}" for k in kinds) + ").",
          "Scored by `score.py` against `defects.json` with the rules in `METHOD.md`.", "",
          "Found = after the hand audit in `audit.json` (every change has a reason); the mechanical score from the keyword rules alone is shown beside it.", "",
          "| Tool | Found (audited) | Mechanical | Behaviour | Code | Tests | Dependency | Findings | Outside the key |", "|---|---|---|---|---|---|---|---|---|"]
    for tool, r in results.items():
        per = [sum(1 for d in defects if d["kind"] == k and d["id"] in r["audited"]) for k in kinds]
        tot = [sum(d["kind"] == k for d in defects) for k in kinds]
        md.append(f"| {tool} | **{len(r['audited'])} / {len(defects)}** | {len(r['found'])} | " + " | ".join(f"{p}/{t}" for p, t in zip(per, tot)) + f" | {r['findings']} | {r['outside']} |")
    md += ["", "## Which tool found which defect (audited)", "", "| Defect | " + " | ".join(results) + " |", "|---|" + "---|" * len(results)]
    for d in defects:
        md.append(f"| {d['id']} {d['title']} | " + " | ".join("yes" if d["id"] in r["audited"] else "" for r in results.values()) + " |")
    union = {did for t, r in results.items() if not t.startswith("TestThisPuppy") for did in r["audited"]}
    md += ["", f"All the other tools together: {len(union)} / {len(defects)}."]
    for run in ("TestThisPuppy (automatic)", "TestThisPuppy (with spec)"):
        if run in results:
            both = union | set(results[run]["audited"])
            md.append(f"Those tools plus {run}: {len(both)} / {len(defects)}.")
    missed = [d["id"] for d in defects if all(d["id"] not in r["audited"] for r in results.values())]
    md.append(f"Found by no tool: {', '.join(missed) if missed else 'none'}.")
    (HERE / "results" / "SCORES.md").write_text("\n".join(md) + "\n", encoding="utf-8")
    (HERE / "results" / "scores.json").write_text(json.dumps(results, indent=1), encoding="utf-8")
    print("\nwritten results/SCORES.md")


if __name__ == "__main__":
    main()
