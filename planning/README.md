# Planning

`issues.json` holds the Phase 0-4 roadmap (design SS17) as a batch spec — one issue
per independently trackable piece of work, in the schema `scripts/batch_create_issues.py`
expects.

When the GitHub repo exists (needs `gh` installed and `gh auth login` done):

1. Edit `"repo"` in `issues.json` to the real `OWNER/trip-planner`.
2. Preview (dry run, touches nothing):  `python scripts/batch_create_issues.py issues.json`
3. Review the preview, then create:     `python scripts/batch_create_issues.py issues.json --create`

The script creates any missing labels and reports them, keeps going past individual
failures, and prints every new issue URL. Labels used: phase-0..4, build, firebase,
auth, maps, feature, places, quality, notifications, calendar, ai.
