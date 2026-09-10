#!/usr/bin/env python3
"""Collect each component's latest released tag and pin all of them into
charts/dws/values.yaml in a single pass, bumping the chart's own patch version once.
Invoked by .github/workflows/chart-release.yml (workflow_dispatch only) -- run this
whenever you deliberately want to cut a chart release, rather than reacting to every
individual component tag.
"""
import os
import re
import subprocess

# Each repository string appears exactly once in values.yaml, immediately followed by
# its sibling `tag:` line -- anchor on that instead of a YAML path, so this survives
# values.yaml being reorganized later.
VALUES_ANCHORS = {
    "dws-admin": ["ghcr.io/tonylibs/dws-admin"],
    "dws-console": ["ghcr.io/tonylibs/dws-console"],
    "dws-controller": ["ghcr.io/tonylibs/dws-controller"],
    "dws-orchestrator": ["ghcr.io/tonylibs/dws-orchestrator"],
    "dws-call-http": ["ghcr.io/tonylibs/dws-call-http"],
    "dws-call-grpc": ["ghcr.io/tonylibs/dws-call-grpc"],
    "dws-call-openapi": ["ghcr.io/tonylibs/dws-call-openapi"],
    "dws-call-asyncapi": ["ghcr.io/tonylibs/dws-call-asyncapi"],
    "dws-run": [
        "ghcr.io/tonylibs/dws-run-shell",
        "ghcr.io/tonylibs/dws-run-script-js",
        "ghcr.io/tonylibs/dws-run-script-python",
    ],
}

VALUES_PATH = "charts/dws/values.yaml"
CHART_PATH = "charts/dws/Chart.yaml"

SEMVER_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")


def latest_tag_version(component: str) -> str | None:
    """Highest-semver tag `<component>-v<version>` reachable in this checkout, or
    None if the component has never been released."""
    out = subprocess.run(
        ["git", "tag", "-l", f"{component}-v*"],
        capture_output=True, text=True, check=True,
    ).stdout.splitlines()
    prefix = f"{component}-v"
    best = None
    for tag in out:
        version = tag[len(prefix):]
        m = SEMVER_RE.match(version)
        if not m:
            continue  # ignore anything non-release-shaped (pre-releases, typos)
        parsed = tuple(int(g) for g in m.groups())
        if best is None or parsed > best[0]:
            best = (parsed, version)
    return best[1] if best else None


def current_tag_value(anchor_repo: str) -> str | None:
    with open(VALUES_PATH) as f:
        content = f.read()
    pattern = re.compile(
        r"repository: " + re.escape(anchor_repo) + r"\n\s+tag: \"?([^\n\"]+)\"?"
    )
    m = pattern.search(content)
    return m.group(1) if m else None


def pin_component(component: str, version: str) -> None:
    with open(VALUES_PATH) as f:
        content = f.read()
    for repo in VALUES_ANCHORS[component]:
        pattern = re.compile(
            r'(repository: ' + re.escape(repo) + r'\n(\s+)tag: )("?[^\n"]+"?)'
        )
        matches = list(pattern.finditer(content))
        if len(matches) != 1:
            raise SystemExit(
                f"expected exactly 1 tag line following '{repo}', found {len(matches)}"
            )
        content = pattern.sub(lambda m: f'{m.group(1)}"{version}"', content, count=1)
    with open(VALUES_PATH, "w") as f:
        f.write(content)


def bump_chart_version(override: str | None) -> str:
    """Bump Chart.yaml's version. With no override, take a patch bump. With an
    override, use it verbatim after checking it's a well-formed semver strictly
    greater than the current version -- lets a run state a deliberate minor/major
    jump, or release the chart with no component change at all, instead of always
    defaulting to patch and requiring a manual edit of the opened PR."""
    with open(CHART_PATH) as f:
        content = f.read()
    m = re.search(r"^version: (\d+)\.(\d+)\.(\d+)$", content, re.M)
    if not m:
        raise SystemExit("could not find a plain 'version: X.Y.Z' line in Chart.yaml")
    major, minor, patch = int(m.group(1)), int(m.group(2)), int(m.group(3))
    current = (major, minor, patch)

    if override:
        om = SEMVER_RE.match(override)
        if not om:
            raise SystemExit(f"chart_version input '{override}' is not a valid X.Y.Z semver")
        new_tuple = tuple(int(g) for g in om.groups())
        if new_tuple <= current:
            raise SystemExit(
                f"chart_version input '{override}' must be greater than the current "
                f"chart version '{major}.{minor}.{patch}'"
            )
        new_version = override
    else:
        new_version = f"{major}.{minor}.{patch + 1}"

    content = content[: m.start()] + f"version: {new_version}" + content[m.end() :]
    with open(CHART_PATH, "w") as f:
        f.write(content)
    return new_version


def main() -> None:
    changes = []  # list of (component, old, new)
    skipped_no_release = []

    for component in VALUES_ANCHORS:
        latest = latest_tag_version(component)
        if latest is None:
            skipped_no_release.append(component)
            continue
        current = current_tag_value(VALUES_ANCHORS[component][0])
        if current == latest:
            continue
        pin_component(component, latest)
        changes.append((component, current, latest))

    github_output = os.environ.get("GITHUB_OUTPUT")
    override = os.environ.get("CHART_VERSION_OVERRIDE", "").strip() or None

    if not changes and not override:
        print("Nothing to do -- every component in values.yaml already matches its latest release tag.")
        if github_output:
            with open(github_output, "a") as f:
                f.write("changed=false\n")
        return

    new_chart_version = bump_chart_version(override)

    if changes:
        summary_lines = [f"- `{c}`: `{old}` -> `{new}`" for c, old, new in changes]
    else:
        summary_lines = ["No component image changed -- chart version set explicitly by this run's input."]
    if skipped_no_release:
        summary_lines.append("")
        summary_lines.append(
            "Not released yet (no `<component>-v*` tag found), left as-is: "
            + ", ".join(f"`{c}`" for c in skipped_no_release)
        )
    summary = "\n".join(summary_lines)

    print(f"Pinned {len(changes)} component(s); chart -> {new_chart_version}")
    print(summary)

    if github_output:
        with open(github_output, "a") as f:
            f.write("changed=true\n")
            f.write(f"chart_version={new_chart_version}\n")
            f.write("summary<<CHART_RELEASE_EOF\n")
            f.write(summary + "\n")
            f.write("CHART_RELEASE_EOF\n")


if __name__ == "__main__":
    main()
