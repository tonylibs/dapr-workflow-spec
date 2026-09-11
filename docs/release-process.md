# Release process

How versions, component images, and the Helm chart get released in this repo.

We keep **semver pinning**: the chart pins each component to a numbered image tag
(`ghcr.io/tonylibs/dws-run-shell:0.2.0`), not `latest` or a commit SHA. Numbered
images only exist once a component's git tag is pushed, so releasing is a chain of
three **deliberate, human-gated** steps with automation in between.

## The three cuts

| # | Action | Trigger | Produces | Gate |
|---|---|---|---|---|
| ① | Bump component version | merge that component's release-please PR | version file + `CHANGELOG.md` + `.release-please-manifest.json` updated | human merges PR |
| ② | Cut component image | run **component-release** workflow | `<component>-v<version>` tag → build fires → `:<version>` image in ghcr.io | human dispatch |
| ③ | Cut chart | run **chart-release** workflow → merge its PR | `values.yaml` pinned + `Chart.yaml` bumped → chart `vX.Y.Z` + OCI push + GitHub Release | human dispatch + merge |

```
   AUTOMATIC (every PR)                DELIBERATE (you decide when)
┌────────────────────────┐   ┌──────────────────────────────────────────────┐
│  merge feature PR      │   │  ① merge component's release-please PR         │
│  → build + test        │   │     → version file + CHANGELOG + manifest      │
│  → push :latest :sha   │   │     (still NO tag, NO :semver image)           │
│    (NO version image)  │   │                                                │
│                        │   │  ② run component-release (pick component)      │
│  release-please keeps  │   │     → pushes <component>-v<version> tag         │
│  a standing release PR │   │     → component build fires → push :<version>  │
│  updated per component │   │                                                │
│                        │   │  ③ run chart-release → merge its PR            │
│                        │   │     → pins :<version> into values.yaml         │
│                        │   │     → helm.yml tags vX.Y.Z, OCI push, Release  │
└────────────────────────┘   └──────────────────────────────────────────────┘
```

## Why component releases are separate from PR merges

Merging a PR to main **does** build and push the component image — but only tagged
`:latest` and `:<short-sha>`. The numbered `:<version>` image is minted **only** by
pushing the `<component>-v<version>` git tag (see each component workflow's
`Extract version from tag` / `type=match` step). So the chart can only pin a semver
that a component release has actually produced.

`release-please.yml` sets `skip-github-release: true` on every package, so merging a
release PR bumps the version/CHANGELOG/manifest but does **not** tag or release —
that's what keeps releases deliberate instead of firing on every merge.

## Ordering rules (enforced)

- **① before ②.** `component-release` reads the version from the manifest and refuses
  to tag unless the manifest **and** the component's `CHANGELOG.md` already record it
  (proof the release PR was merged). Override with `force=true` only for a bootstrap
  component (no CHANGELOG yet) or a hotfix tag.
- **② before ③.** `chart_release.py` pins the highest existing `<component>-v*` tag.
  If you run chart-release before the component tag exists, it pins nothing new.

## Common flows

- **Ship one component:** ① merge its release PR → ② run `component-release` for it.
  Chart untouched.
- **Ship a chart rollup:** ①+② for each component you want to advance, then ③ once.
  `chart-release` sweeps every moved component into a single chart bump.
- **Chart-only change** (template/values, no component moved): skip ①/②, run
  `chart-release` with an explicit `chart_version` input.

## Workflows involved

| Workflow | File | Trigger |
|---|---|---|
| release-please | `.github/workflows/release-please.yml` | push to main (auto) |
| component-release | `.github/workflows/component-release.yml` | `workflow_dispatch` |
| chart-release | `.github/workflows/chart-release.yml` | `workflow_dispatch` |
| helm-chart (publish) | `.github/workflows/helm.yml` | push to `charts/**` on main |
| per-component build | `.github/workflows/dws-*.yml` | push to path / `<component>-v*` tag |

## Notes on current state

- `values.yaml` today pins `latest` for most components and `"1.0"` for
  controller/admin/console. `chart_release.py` self-heals these to real semver on the
  next ③ run — but each of those components needs a `<component>-v<version>` tag to
  exist first. Run ② once per component to mint the images, then the first ③
  normalizes `values.yaml`.
- `"1.0"` is not valid semver; the manifest already carries proper `X.Y.Z` values, so
  the pins converge to `X.Y.Z` after the first component + chart release.
