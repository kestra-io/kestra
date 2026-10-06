#!/usr/bin/env python3
"""Fail when a dependency in a license report has a denied or an unknown license.

Usage:
    ./gradlew licenseReport
    python3 dev-tools/check-licenses.py [build/reports/licenses/licenseReport.json] [--sarif license-check.sarif]

    cd ui && npx license-compliance --report detailed --production --format json > /tmp/ui-licenses.json
    python3 dev-tools/check-licenses.py /tmp/ui-licenses.json

Each license is classified against two lists:
- the deny-list (GPL, AGPL, SSPL...) is reported as CRITICAL,
- a license in neither list, including a missing one, is reported as HIGH,
- the allow-list (permissive and weak copyleft licenses) is accepted silently.

The report format is detected from its entries: Gradle lists the alternatives of a dual-licensed
library in `licenses`, npm gives one SPDX expression in `license`. Alternatives (Gradle licenses and
SPDX `OR`) take the best class and SPDX `AND` members take the worst one. The script exits with 1
when a dependency is CRITICAL or HIGH, unless it is listed in ALLOWED_DEPENDENCIES.

With `--sarif`, the findings are also written as a SARIF 2.1.0 file, which can be uploaded as code scanning results.
Dependencies accepted by exception are included as suppressed results, or left out with `--sarif-omit-accepted`
for a consumer that ignores suppressions.
"""
import argparse
import json
import re
import sys
from collections import Counter
from enum import IntEnum
from pathlib import Path

DEFAULT_REPORT = Path(__file__).resolve().parent.parent / "build/reports/licenses/licenseReport.json"


class Severity(IntEnum):
    """A higher value is a worse license."""
    ALLOWED = 0
    HIGH = 1
    CRITICAL = 2


# Strong copyleft or source-available licenses that cannot be shipped with Kestra.
DENIED = re.compile(
    r"\b(a?gpl(?:v?\d|\b)|(?:general public license|sspl|server side public|busl|business source|commons clause|"
    r"cc[- ]by[- ]nc|non[- ]?commercial|proprietary|unlicensed)\b)",
    re.IGNORECASE,
)
# GPL with a linking exception, and the LGPL weak copyleft, are acceptable for a dynamically linked library.
LINKING_EXCEPTION = re.compile(r"\b(cpe|classpath[- ]exception|universal foss exception)\b", re.IGNORECASE)
LGPL = re.compile(r"\blgpl(?:v?\d|\b)|(?:lesser|library) general public license", re.IGNORECASE)
OR_LATER = re.compile(r"\bor (?:any )?later(?: version)?\b", re.IGNORECASE)
# Permissive licenses and file-level weak copyleft.
ALLOWED = re.compile(
    r"\bapache|\bmit\b|\bbsd|\bisc\b|\b0bsd\b|\bunlicense\b|\bcc0\b|public domain|\bblueoak|\bpython-2\.0\b|\bpsf\b|"
    r"\bmpl\b|mozilla public|\bepl\b|eclipse public|\bedl\b|eclipse distribution|\bcddl\b|bouncy castle|\bzlib\b|\bwtfpl\b|\bw3c\b|\bgo license\b",
    re.IGNORECASE,
)

SARIF_RULES = {
    "denied-license": (
        "Dependency with a denied license",
        "The dependency is only available under a license that is not acceptable.",
        "9.5",
        Severity.CRITICAL,
    ),
    "unknown-license": (
        "Dependency with an unknown license",
        "The dependency has no license, or a license that is neither allowed nor denied.",
        "7.5",
        Severity.HIGH,
    ),
}
MANIFESTS = {"gradle": "build.gradle", "npm": "ui/package.json"}

# Dependencies (Gradle group:artifact, npm name) reviewed by hand and accepted despite a denied or unknown license.
ALLOWED_DEPENDENCIES: set[str] = {
    "@kestra-io/design-system",
    "@kestra-io/slot-contracts",
    "@kestra-io/topology",
    "jsonify",  # Public Domain according to its package.json
    "khroma",  # MIT according to its license file
}


def split_expression(expression: str, operator: str) -> list[str]:
    """Split an SPDX expression on a top-level operator, ignoring the ones inside parentheses."""
    parts, depth, start = [], 0, 0
    for match in re.finditer(r"[()]|\s+" + operator + r"\s+", expression, re.IGNORECASE):
        if match.group() == "(":
            depth += 1
        elif match.group() == ")":
            depth -= 1
        elif depth == 0:
            parts.append(expression[start:match.start()])
            start = match.end()
    return [*parts, expression[start:]]


def unwrap(expression: str) -> str:
    """Strip the parentheses wrapping the whole expression, as in `(MIT OR Apache-2.0)`."""
    expression = expression.strip()
    while expression.startswith("(") and expression.endswith(")"):
        depth = 0
        for char in expression[:-1]:
            depth += (char == "(") - (char == ")")
            if depth == 0:
                return expression
        expression = expression[1:-1].strip()
    return expression


def classify(license_name: str) -> Severity:
    license_name = unwrap(OR_LATER.sub("", license_name))
    alternatives = split_expression(license_name, "OR")
    if len(alternatives) > 1:
        return min(classify(alternative) for alternative in alternatives)
    members = split_expression(license_name, "AND")
    if len(members) > 1:
        return max(classify(member) for member in members)
    if LINKING_EXCEPTION.search(license_name):
        return Severity.ALLOWED
    if LGPL.search(license_name):
        return Severity.CRITICAL if DENIED.search(LGPL.sub("", license_name)) else Severity.ALLOWED
    if DENIED.search(license_name):
        return Severity.CRITICAL
    return Severity.ALLOWED if ALLOWED.search(license_name) else Severity.HIGH


def classify_all(licenses: list[str]) -> Severity:
    """Classify the alternative licenses of a dependency, a dependency without any is unknown."""
    return min((classify(name) for name in licenses), default=Severity.HIGH)


def sarif_result(rule_id: str, dependency: str, package: str, version: str, licenses: str, manifest: str, suppressed: bool) -> dict:
    severity = SARIF_RULES[rule_id][3]
    result = {
        "ruleId": rule_id,
        "level": "error",
        # The labelled lines are the format `report-sarif` reads the package back from, which makes the id of each finding unique.
        "message": {"text": f"{severity.name} license finding.\nPackage: {package}\nInstalled Version: {version or 'unknown'}\nLicense: {' '.join(licenses.split())}"},
        "locations": [{"physicalLocation": {"artifactLocation": {"uri": manifest, "uriBaseId": "%SRCROOT%"}, "region": {"startLine": 1}}}],
        "partialFingerprints": {"licenseCheck/v1": f"{rule_id}:{dependency}"},
        "properties": {"severity": severity.name},
    }
    if suppressed:
        result["suppressions"] = [{"kind": "inSource", "status": "accepted", "justification": "Listed in ALLOWED_DEPENDENCIES of dev-tools/check-licenses.py."}]
    return result


def write_sarif(path: Path, results: list[dict]) -> None:
    rules = [
        {
            "id": rule_id,
            "name": rule_id,
            "defaultConfiguration": {"level": "error"},
            "shortDescription": {"text": title},
            "fullDescription": {"text": description},
            "properties": {"security-severity": security_severity, "tags": ["security", "license"]},
        }
        for rule_id, (title, description, security_severity, _) in SARIF_RULES.items()
    ]
    sarif = {
        "$schema": "https://json.schemastore.org/sarif-2.1.0.json",
        "version": "2.1.0",
        "runs": [{"tool": {"driver": {"name": "license-check", "rules": rules}}, "results": results}],
    }
    path.write_text(json.dumps(sarif, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description="Check a license report for denied or unknown licenses.")
    parser.add_argument("report", nargs="?", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--sarif", type=Path, help="also write the findings to this SARIF file")
    parser.add_argument("--sarif-omit-accepted", action="store_true", help="leave the dependencies accepted by exception out of the SARIF file instead of marking them suppressed")
    args = parser.parse_args()
    report = args.report
    if not report.is_file():
        print(f"License report '{report}' not found, run './gradlew licenseReport' first.", file=sys.stderr)
        return 2

    findings: dict[Severity, list[tuple[str, str]]] = {Severity.CRITICAL: [], Severity.HIGH: []}
    accepted: list[tuple[str, str]] = []
    sarif_results: dict[tuple[str, str], dict] = {}
    license_counts: Counter[str] = Counter()
    with report.open(encoding="utf-8") as file:
        for row in json.load(file):
            manifest = MANIFESTS["gradle" if "licenses" in row else "npm"]
            if "licenses" in row:
                dependency = row["dependency"]
                package, _, version = dependency.rpartition(":")
                if package.count(":") == 0:
                    package, version = dependency, ""
                licenses = [(entry.get("license") or "").strip() for entry in row["licenses"]]
            else:
                dependency = "{}@{}".format(row["name"], row["version"])
                package, version = row["name"], row["version"]
                licenses = [(row.get("license") or "").strip()]
            license_counts.update(name or "(none)" for name in licenses or [""])
            severity = classify_all(licenses)
            if severity == Severity.ALLOWED:
                continue
            is_accepted = ":".join(dependency.split(":")[:2]) in ALLOWED_DEPENDENCIES or row.get("name") in ALLOWED_DEPENDENCIES
            license_names = ", ".join(name or "(none)" for name in licenses) or "(none)"
            rule_id = "denied-license" if severity == Severity.CRITICAL else "unknown-license"
            (accepted if is_accepted else findings[severity]).append((dependency, license_names))
            if is_accepted and args.sarif_omit_accepted:
                continue
            sarif_results[(rule_id, dependency)] = sarif_result(rule_id, dependency, package, version, license_names, manifest, is_accepted)

    print("Dependencies per license (a dual-licensed dependency counts once per license):")
    for name, count in sorted(license_counts.items(), key=lambda item: (-item[1], item[0])):
        print(f"  {count:4d}  {name}")
    print()

    if accepted:
        print("Dependencies accepted by exception (ALLOWED_DEPENDENCIES) despite a denied or unknown license:")
        for dependency, licenses in sorted(accepted):
            print(f"  {dependency}: {licenses}")
    titles = {Severity.CRITICAL: "CRITICAL, dependencies with a denied license:", Severity.HIGH: "HIGH, dependencies with an unknown license:"}
    for severity in (Severity.CRITICAL, Severity.HIGH):
        if findings[severity]:
            print(titles[severity])
            for dependency, licenses in sorted(findings[severity]):
                print(f"  {dependency}: {licenses}")
    failed = any(findings.values())
    if not failed:
        print("No dependency with a denied or unknown license, apart from the exceptions above." if accepted else "No dependency with a denied or unknown license.")
    if args.sarif:
        write_sarif(args.sarif, list(sarif_results.values()))
        print(f"SARIF report written to '{args.sarif}'.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
