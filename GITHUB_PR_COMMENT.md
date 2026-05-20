# PR Comment Template for GitHub

## Title
```
fix: Display plugin context in trigger card names (#16078)
```

## Description
Fixes issue #16078 where trigger cards on the "Triggers → Add" page displayed generic names like "RealtimeTrigger" or "Trigger" without plugin context, making them indistinguishable.

### Changes Made

#### Backend (`PluginController.java`)
- Modified `toApiTriggerPlugin()` method to use new `buildTriggerDisplayName()` helper
- New method generates human-readable trigger names with plugin context:
  - Core triggers: Returns simple class name (e.g., "Schedule")
  - External plugins: Combines plugin name + trigger type (e.g., "Kafka Realtime", "AWS SQS")
  - Special handling for generic names ("RealtimeTrigger" → "PluginName Realtime")

#### Frontend (`triggerCatalog.ts`)
- Updated `triggerDisplayName()` to properly handle backend-provided names
- Maintains backward compatibility with fallback logic
- Added unit tests with 100% pass rate

### Result
Trigger cards now display meaningful, distinctive names:
- ❌ Before: "RealtimeTrigger", "Trigger", "Trigger", "Trigger"
- ✅ After: "Kafka Realtime", "AWS SQS", "MongoDB", "Postgres"

### Testing
- ✅ 10 unit tests passing (new test file: `triggerCatalog.test.ts`)
- ✅ Tests cover edge cases: generic names, complex paths, MCP tools
- ✅ Verified with local development environment

### Checklist
- [x] Code follows project conventions
- [x] Unit tests written and passing
- [x] Backend changes compiled successfully
- [x] Frontend changes validated with TypeScript
- [x] Backward compatible with existing code
- [x] No breaking changes

---

## Additional Notes for Reviewers

### Code Quality
- Uses existing metadata from `RegisteredPlugin.title()` - no new dependencies
- Maintains separation of concerns between backend (name generation) and frontend (display)
- Graceful fallback for unknown trigger patterns

### Performance Impact
- Minimal - only applies string manipulation during API response generation
- No database queries or additional processing

### User Impact
- Direct improvement to UX - users can now identify triggers at a glance
- No migration or configuration needed

---

## Screenshots/Examples
When you run locally:
1. Navigate to Tenant → Triggers → Add
2. Expand "See more" in Realtime Triggers section
3. Observe trigger cards now show:
   - "Kafka Realtime" instead of "RealtimeTrigger"
   - "AWS SQS" instead of "Trigger"
   - "MongoDB" with proper name
   - And so on for all external plugins
