# How to Create and Submit the PR

## Quick Summary
✅ **All changes ready for PR**
- Backend: 1 file modified (Java)
- Frontend: 2 files (1 modified, 1 new test)
- Unit tests: **10/10 passing**
- Status: Ready to submit

## Step-by-Step Guide

### Step 1: Fork & Clone (if not already done)
```bash
# Fork the repository on GitHub
# Then clone your fork
git clone https://github.com/YOUR-USERNAME/kestra.git
cd kestra
git remote add upstream https://github.com/kestra-io/kestra.git
```

### Step 2: Create Feature Branch
```bash
# Update from upstream
git fetch upstream
git checkout -b fix/trigger-display-names upstream/develop

# Or use a more descriptive name:
git checkout -b fix/issue-16078-trigger-names upstream/develop
```

### Step 3: Verify Changes
```bash
# Check what files were modified
git status

# Expected output:
# Modified:   webserver/src/main/java/io/kestra/webserver/controllers/api/PluginController.java
# Modified:   ui/src/components/admin/triggers/triggerCatalog.ts
# Untracked:  ui/src/components/admin/triggers/triggerCatalog.test.ts

# View the changes
git diff webserver/src/main/java/io/kestra/webserver/controllers/api/PluginController.java
git diff ui/src/components/admin/triggers/triggerCatalog.ts
git diff ui/src/components/admin/triggers/triggerCatalog.test.ts
```

### Step 4: Stage & Commit
```bash
# Stage all changes
git add .

# Or stage individual files:
git add webserver/src/main/java/io/kestra/webserver/controllers/api/PluginController.java
git add ui/src/components/admin/triggers/triggerCatalog.ts
git add ui/src/components/admin/triggers/triggerCatalog.test.ts

# Commit with descriptive message
git commit -m "fix: Display plugin context in trigger card names (#16078)

- Backend: Added buildTriggerDisplayName() to PluginController
  to generate meaningful trigger names with plugin context
- Frontend: Updated triggerDisplayName() to use new names
- Added: Comprehensive unit tests (10 tests, all passing)

Examples:
- 'RealtimeTrigger' → 'Kafka Realtime'
- 'Trigger' → 'MongoDB'
- 'Trigger' → 'AWS SQS'

Fixes #16078"
```

### Step 5: Push to Your Fork
```bash
# Push to your fork
git push origin fix/trigger-display-names

# Or if already have an upstream:
git push origin fix/issue-16078-trigger-names
```

### Step 6: Create Pull Request on GitHub

1. Go to https://github.com/YOUR-USERNAME/kestra
2. You should see a banner suggesting to create a PR for your pushed branch
3. Click "Compare & pull request"
4. Or navigate to: https://github.com/kestra-io/kestra/pulls and click "New Pull Request"

### Step 7: Fill in PR Details

#### PR Title
```
fix: Display plugin context in trigger card names (#16078)
```

#### PR Description
Copy and paste from [GITHUB_PR_COMMENT.md](../GITHUB_PR_COMMENT.md)

Or use this template:

```markdown
## Issue
Fixes #16078 - [Triggers] Many cards display generic names like "RealtimeTrigger" or "Trigger"

## Changes
- **Backend**: Added `buildTriggerDisplayName()` method to `PluginController.java` to generate meaningful trigger names with plugin context
- **Frontend**: Updated `triggerDisplayName()` in `triggerCatalog.ts` to properly handle improved names
- **Tests**: Added comprehensive unit tests (10 tests, all passing)

## Before & After
| Before | After |
|--------|-------|
| "RealtimeTrigger" | "Kafka Realtime" |
| "Trigger" | "MongoDB" |
| "Trigger" | "AWS SQS" |
| "Trigger" | "Postgres" |

## Testing
- ✅ 10 unit tests passing
- ✅ TypeScript validation passed
- ✅ No breaking changes
- ✅ Backward compatible

## Files Changed
- `webserver/src/main/java/io/kestra/webserver/controllers/api/PluginController.java`
- `ui/src/components/admin/triggers/triggerCatalog.ts`
- `ui/src/components/admin/triggers/triggerCatalog.test.ts` (new)
```

### Step 8: Check PR Requirements

Before submitting, verify:
- [ ] PR title is descriptive
- [ ] PR description explains the problem and solution
- [ ] Related issue is mentioned (#16078)
- [ ] Files changed are correct (3 files)
- [ ] All commits have meaningful messages
- [ ] No merge conflicts

### Step 9: Submit PR
Click "Create Pull Request" button

## After Submission

### Monitor PR Status
1. Check that CI/CD pipeline passes (GitHub Actions)
2. Wait for code review from maintainers
3. Address any requested changes

### Respond to Feedback
If maintainers request changes:
```bash
# Make the changes locally
# Stage and commit
git add <changed-files>
git commit -m "Address review feedback: <description>"

# Push to the same branch
git push origin fix/issue-16078-trigger-names

# The PR will automatically update
```

### Common Review Questions

**Q: Why change both backend and frontend?**
A: The backend generates better names using plugin context, and the frontend is updated to properly utilize these improved names.

**Q: Why add tests?**
A: Tests ensure the display name logic works correctly for various trigger types and edge cases, preventing regressions.

**Q: What about performance?**
A: No performance impact - only simple string manipulation during API response generation, no database queries.

**Q: Will this break existing code?**
A: No, it's fully backward compatible. Existing trigger functionality is unchanged.

## PR Checklist

### Before You Submit
- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] Code follows project style guide
- [ ] No unnecessary dependencies added
- [ ] Changes are isolated to trigger display names
- [ ] Comments explain non-obvious logic

### After You Submit
- [ ] GitHub PR created
- [ ] CI/CD pipeline visible and running
- [ ] No merge conflicts
- [ ] Issue (#16078) is linked
- [ ] Description is clear and complete

## Reference Documents

For more details, see:
- [PR_SUMMARY.md](../PR_SUMMARY.md) - Executive summary
- [TESTING_GUIDE.md](../TESTING_GUIDE.md) - How to test the changes
- [CODE_CHANGES.md](../CODE_CHANGES.md) - Detailed code changes
- [GITHUB_PR_COMMENT.md](../GITHUB_PR_COMMENT.md) - PR comment template

## Troubleshooting

### "Merge conflict" error
```bash
# Update your branch from main
git fetch upstream
git rebase upstream/develop

# Resolve conflicts in your editor, then:
git add <resolved-files>
git rebase --continue
git push origin --force-with-lease fix/issue-16078-trigger-names
```

### "CI pipeline failing"
1. Check the GitHub Actions logs
2. Usually related to:
   - Code style issues (use project's formatter)
   - Missing imports
   - Lint errors
3. Fix locally and push again

### "Request changes from reviewer"
1. Make the requested changes
2. Commit with clear message
3. Push to the same branch
4. Add comment explaining the changes
5. PR will update automatically

## Success Indicators

Your PR is ready when:
- ✅ All GitHub checks passing
- ✅ At least one approval from maintainer
- ✅ No "Request changes" statuses
- ✅ All conversations resolved
- ✅ No merge conflicts
