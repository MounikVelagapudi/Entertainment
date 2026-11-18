# Log entry formats

## WORKLOG.md entry
```
## YYYY-MM-DD — {{task title}}
- **Branch:** {{branch}}
- **Summary:** {{1–3 lines: what changed and why}}
- **Files:** {{paths}}
- **Review:** {{APPROVE / CHANGES_REQUESTED → fixed in N loops}} — {{key findings, or "clean"}}
- **QA:** {{PASS/FAIL}} — {{tests added; command → counts}}
- **Follow-ups:** {{open items, or "none"}}
```

## DECISIONS.md entry (only if there was a non-obvious call)
```
## YYYY-MM-DD — {{decision title}}
- **Context:** {{what forced a choice}}
- **Decision:** {{what we picked}}
- **Alternatives:** {{what we didn't pick}}
- **Why:** {{reason}}
- **Task:** {{link to WORKLOG entry title}}
```
