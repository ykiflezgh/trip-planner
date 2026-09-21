#!/usr/bin/env python3
"""
Batch-create GitHub Issues from a JSON spec using the gh CLI.

Default behavior is a DRY RUN: it prints what would be created and touches nothing.
Pass --create to actually file the issues.

Spec format (issues.json):
{
  "repo": "OWNER/REPO",                  # optional if --repo is passed or repo is in cwd
  "issues": [
    {
      "title": "...",                    # required
      "body": "...",                     # optional, markdown, may contain newlines
      "template": "bug_report.md",       # optional; merges template labels/assignees/title prefix
      "labels": ["bug", "priority:high"],# optional
      "assignees": ["@me", "octocat"],   # optional
      "milestone": "v1.0",               # optional (must already exist)
      "project": "Roadmap"               # optional
    }
  ]
}

Examples:
    python batch_create_issues.py issues.json                 # preview only
    python batch_create_issues.py issues.json --create        # actually create
    python batch_create_issues.py issues.json --repo me/kappa-pilot --create
    python batch_create_issues.py --list-templates            # show repo's issue templates
"""
import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path


def run(cmd, capture=True):
    """Run a command, returning (returncode, stdout, stderr)."""
    try:
        proc = subprocess.run(
            cmd,
            stdout=subprocess.PIPE if capture else None,
            stderr=subprocess.PIPE if capture else None,
            text=True,
        )
    except FileNotFoundError:
        return 127, "", f"command not found: {cmd[0]}"
    return proc.returncode, (proc.stdout or ""), (proc.stderr or "")


def fail(msg):
    print(f"ERROR: {msg}", file=sys.stderr)
    sys.exit(1)


try:
    import yaml  # optional; improves parsing of YAML-list frontmatter and issue forms
    _HAVE_YAML = True
except ImportError:
    _HAVE_YAML = False


def _unquote_title(value):
    """Unquote a frontmatter title while preserving an intentional trailing space."""
    s = str(value)
    t = s.strip()
    if len(t) >= 2 and t[0] == t[-1] and t[0] in "'\"":
        return t[1:-1]  # minimal parser left the quotes on; keep inner spaces
    return s            # yaml already unquoted; keep its trailing space


def _coerce_list(value):
    """Normalize a frontmatter value into a list of strings."""
    if value is None:
        return []
    if isinstance(value, list):
        return [str(v).strip() for v in value if str(v).strip()]
    s = str(value).strip().strip("[]")
    return [p.strip().strip("'\"") for p in s.split(",") if p.strip()]


def template_dirs(repo_dir):
    base = Path(repo_dir)
    return [base / ".github" / "ISSUE_TEMPLATE",
            base / "docs" / "ISSUE_TEMPLATE",
            base / ".github",
            base]


def find_templates(repo_dir):
    """Return {display_name: path} for issue templates found in a local checkout."""
    found = {}
    for d in template_dirs(repo_dir):
        if not d.is_dir():
            continue
        for p in sorted(d.glob("*")):
            if p.suffix.lower() not in (".md", ".yml", ".yaml"):
                continue
            if p.name.lower() in ("config.yml", "config.yaml"):
                continue
            # In .github/ root only the legacy single-template file counts.
            if d.name == ".github" and p.name.lower() not in (
                    "issue_template.md", "issue_template.yml", "issue_template.yaml"):
                continue
            found.setdefault(p.name, p)
    return found


def resolve_template_path(name, repo_dir):
    """Resolve a template reference (filename, name w/o ext, or path) to a Path."""
    p = Path(name)
    if p.is_file():
        return p
    templates = find_templates(repo_dir)
    if name in templates:
        return templates[name]
    stem = Path(name).stem.lower()
    for fname, path in templates.items():
        if Path(fname).stem.lower() == stem:
            return path
    return None


def parse_template(path):
    """Return {'title','labels','assignees'} defaults from a template file."""
    text = path.read_text(encoding="utf-8", errors="replace")
    meta = {}
    if path.suffix.lower() in (".yml", ".yaml"):
        # Issue Form: top-level title/labels/assignees apply.
        if _HAVE_YAML:
            try:
                meta = yaml.safe_load(text) or {}
            except yaml.YAMLError:
                meta = {}
    else:
        # Markdown template: parse the --- frontmatter block.
        if text.lstrip().startswith("---"):
            body = text.lstrip()[3:]
            end = body.find("\n---")
            if end != -1:
                fm = body[:end]
                if _HAVE_YAML:
                    try:
                        meta = yaml.safe_load(fm) or {}
                    except yaml.YAMLError:
                        meta = {}
                if not meta:  # minimal fallback for inline key: value lines
                    for line in fm.splitlines():
                        if ":" in line and not line.lstrip().startswith("-"):
                            k, _, v = line.partition(":")
                            meta[k.strip()] = v.strip()
    return {
        "title": _unquote_title(meta.get("title", "")),
        "labels": _coerce_list(meta.get("labels")),
        "assignees": _coerce_list(meta.get("assignees")),
    }


def apply_template(issue, repo_dir):
    """Merge a template's frontmatter defaults into an issue dict (issue values win)."""
    ref = issue.get("template")
    if not ref:
        return
    path = resolve_template_path(ref, repo_dir)
    if path is None:
        print(f"  ! template '{ref}' not found under {repo_dir} — using issue fields as-is",
              file=sys.stderr)
        return
    t = parse_template(path)
    merged = list(t["labels"])
    for l in issue.get("labels", []):
        if l not in merged:
            merged.append(l)
    issue["labels"] = merged
    if not issue.get("assignees") and t["assignees"]:
        issue["assignees"] = t["assignees"]
    prefix = t["title"]
    if prefix and not issue.get("title", "").startswith(prefix):
        issue["title"] = prefix + issue.get("title", "")


def check_gh():
    rc, _, _ = run(["gh", "--version"])
    if rc != 0:
        fail("gh CLI not found. Install it from https://cli.github.com and retry.")
    rc, _, err = run(["gh", "auth", "status"])
    if rc != 0:
        fail("Not authenticated with gh. Run `gh auth login` and retry.\n" + err.strip())


def resolve_repo(spec, arg_repo):
    if arg_repo:
        return arg_repo
    if spec.get("repo"):
        return spec["repo"]
    rc, out, _ = run(["gh", "repo", "view", "--json", "nameWithOwner",
                      "-q", ".nameWithOwner"])
    if rc == 0 and out.strip():
        return out.strip()
    fail("No repo specified. Add \"repo\" to the spec, pass --repo OWNER/REPO, "
         "or run from inside a git checkout of the repo.")


def existing_labels(repo):
    rc, out, _ = run(["gh", "label", "list", "--repo", repo,
                      "--limit", "500", "--json", "name", "-q", ".[].name"])
    if rc != 0:
        return set()
    return {line.strip() for line in out.splitlines() if line.strip()}


def ensure_labels(repo, wanted, create_missing):
    """Make sure labels exist. Returns the set of labels that are safe to use."""
    have = existing_labels(repo)
    missing = sorted(wanted - have)
    usable = set(have)
    if not missing:
        return wanted, [], []
    created, skipped = [], []
    for name in missing:
        if create_missing:
            rc, _, err = run(["gh", "label", "create", name, "--repo", repo,
                              "--color", "ededed"])
            if rc == 0:
                created.append(name)
                usable.add(name)
            else:
                skipped.append(name)
                print(f"  ! could not create label '{name}': {err.strip()}",
                      file=sys.stderr)
        else:
            skipped.append(name)
    return usable, created, skipped


def preview(repo, issues):
    print(f"\nDRY RUN — would create {len(issues)} issue(s) on {repo}:\n")
    for i, it in enumerate(issues, 1):
        print(f"  [{i}] {it.get('title', '(no title)')}")
        meta = []
        if it.get("labels"):
            meta.append("labels: " + ", ".join(it["labels"]))
        if it.get("assignees"):
            meta.append("assignees: " + ", ".join(it["assignees"]))
        if it.get("milestone"):
            meta.append(f"milestone: {it['milestone']}")
        if it.get("project"):
            meta.append(f"project: {it['project']}")
        if meta:
            print("       " + " | ".join(meta))
        body = (it.get("body") or "").strip()
        if body:
            first = body.splitlines()[0]
            more = "" if len(body.splitlines()) == 1 else " ..."
            print(f"       body: {first[:80]}{more}")
        print()
    print("Nothing was created. Re-run with --create to file these issues.\n")


def create_one(repo, it, assignee_default):
    cmd = ["gh", "issue", "create", "--repo", repo,
           "--title", it["title"]]
    body = it.get("body", "")
    tmp = None
    if body:
        tmp = tempfile.NamedTemporaryFile("w", suffix=".md", delete=False)
        tmp.write(body)
        tmp.close()
        cmd += ["--body-file", tmp.name]
    else:
        cmd += ["--body", ""]
    for lbl in it.get("labels", []):
        cmd += ["--label", lbl]
    assignees = it.get("assignees") or ([assignee_default] if assignee_default else [])
    for a in assignees:
        cmd += ["--assignee", a]
    if it.get("milestone"):
        cmd += ["--milestone", it["milestone"]]
    if it.get("project"):
        cmd += ["--project", it["project"]]
    rc, out, err = run(cmd)
    if tmp:
        Path(tmp.name).unlink(missing_ok=True)
    if rc != 0:
        return None, err.strip()
    return out.strip(), None


def main():
    ap = argparse.ArgumentParser(description="Batch-create GitHub Issues from a JSON spec.")
    ap.add_argument("spec", nargs="?", help="Path to the issues JSON spec file.")
    ap.add_argument("--create", action="store_true",
                    help="Actually create issues. Without this, runs as a dry run.")
    ap.add_argument("--repo", help="Override OWNER/REPO from the spec.")
    ap.add_argument("--repo-dir", default=".",
                    help="Local checkout dir to read issue templates from (default: cwd).")
    ap.add_argument("--list-templates", action="store_true",
                    help="List issue templates found under --repo-dir and exit.")
    ap.add_argument("--assignee-default",
                    help="Assignee to use for issues that don't specify one (e.g. @me).")
    grp = ap.add_mutually_exclusive_group()
    grp.add_argument("--create-missing-labels", dest="create_labels",
                     action="store_true", default=True,
                     help="Create labels that don't exist yet (default).")
    grp.add_argument("--skip-missing-labels", dest="create_labels",
                     action="store_false",
                     help="Drop labels that don't exist instead of creating them.")
    args = ap.parse_args()

    if args.list_templates:
        templates = find_templates(args.repo_dir)
        if not templates:
            print(f"No issue templates found under {args.repo_dir}")
        else:
            print(f"Issue templates under {args.repo_dir}:")
            for name in templates:
                print(f"  - {name}")
        return

    if not args.spec:
        fail("A spec file is required (or use --list-templates).")

    spec_path = Path(args.spec)
    if not spec_path.exists():
        fail(f"Spec file not found: {spec_path}")
    try:
        spec = json.loads(spec_path.read_text())
    except json.JSONDecodeError as e:
        fail(f"Spec is not valid JSON: {e}")

    issues = spec.get("issues")
    if not isinstance(issues, list) or not issues:
        fail("Spec must contain a non-empty \"issues\" array.")
    for i, it in enumerate(issues, 1):
        if not it.get("title"):
            fail(f"Issue #{i} is missing a required \"title\".")

    # Merge any referenced issue templates (frontmatter defaults) before preview/create.
    for it in issues:
        apply_template(it, args.repo_dir)

    # Dry run: pure local, no gh calls. Safe to run anywhere.
    if not args.create:
        repo = args.repo or spec.get("repo") or "OWNER/REPO (resolve at create time)"
        preview(repo, issues)
        return

    check_gh()
    repo = resolve_repo(spec, args.repo)

    wanted = {l for it in issues for l in it.get("labels", [])}
    if wanted:
        usable, created, skipped = ensure_labels(repo, wanted, args.create_labels)
        if created:
            print(f"Created {len(created)} new label(s): {', '.join(created)}")
        if skipped:
            print(f"Skipping {len(skipped)} unavailable label(s): {', '.join(skipped)}")
            for it in issues:
                it["labels"] = [l for l in it.get("labels", []) if l in usable]

    print(f"\nCreating {len(issues)} issue(s) on {repo}...\n")
    created_urls, failures = [], []
    for i, it in enumerate(issues, 1):
        url, err = create_one(repo, it, args.assignee_default)
        if url:
            created_urls.append(url)
            print(f"  [{i}] OK  {it['title']}\n       {url}")
        else:
            failures.append((it["title"], err))
            print(f"  [{i}] FAIL  {it['title']}\n       {err}", file=sys.stderr)

    print(f"\nDone. Created {len(created_urls)} of {len(issues)} issue(s).")
    if failures:
        print(f"{len(failures)} failed:")
        for title, err in failures:
            print(f"  - {title}: {err}")
        sys.exit(1)


if __name__ == "__main__":
    main()
