# Fix: Display Plugin Context in Trigger Card Names

## Issue
#16078 - [Triggers] Many cards display generic names like "RealtimeTrigger" or "Trigger"

## Problem Description
On the Triggers → Add page, trigger cards from external plugins displayed generic class names without plugin context:
- All Realtime triggers showed "RealtimeTrigger" regardless of source (Kafka, AWS SQS, MongoDB, etc.)
- Generic "Trigger" cards had no distinguishing information
- Users couldn't tell triggers apart at a glance and had to read descriptions or hover

## Solution
Enhanced the trigger display name generation on both backend and frontend to include plugin context.

### Backend Changes
**File**: `webserver/src/main/java/io/kestra/webserver/controllers/api/PluginController.java`

**Changes**:
1. Modified `toApiTriggerPlugin()` to call new `buildTriggerDisplayName()` helper method
2. Created `buildTriggerDisplayName()` that:
   - Gets the plugin title from `RegisteredPlugin.title()`
   - For core triggers (Kestra built-in): returns just the class name (e.g., "Schedule")
   - For external plugins: combines plugin name with trigger class name
   - Handles edge cases like "RealtimeTrigger" → "PluginName Realtime"
   - Examples output: "Kafka Realtime", "AWS SQS", "MongoDB Trigger"

### Frontend Changes
**File**: `ui/src/components/admin/triggers/triggerCatalog.ts`

**Changes**:
1. Updated `triggerDisplayName()` function to exclude "RealtimeTrigger" from fallback logic
2. Now properly utilizes backend-provided names with plugin context
3. Maintains fallback for edge cases

## Testing

### Unit Tests
Created comprehensive unit tests in `ui/src/components/admin/triggers/triggerCatalog.test.ts`

**Test Results**: ✅ All 10 tests passing

```
✓ triggerDisplayName (6 tests)
  ✓ should return the name when name is provided and not generic
  ✓ should return formatted plugin name when trigger name is 'Trigger'
  ✓ should return formatted plugin name when trigger name is 'RealtimeTrigger'
  ✓ should return formatted class name from type when no name provided
  ✓ should handle core triggers correctly
  ✓ should handle complex plugin paths

✓ isMcpTrigger (4 tests)
  ✓ should return true for McpTool type
  ✓ should return true for classes ending with McpTool
  ✓ should return true for properly formatted McpTool classes
  ✓ should return false for non-McpTool triggers
```

## Before & After

### Before
| Card | Displayed Name |
|------|---|
| Kafka | RealtimeTrigger |
| AWS SQS | RealtimeTrigger |
| MongoDB | Trigger |
| PostgreSQL | Trigger |

### After
| Card | Displayed Name |
|------|---|
| Kafka | Kafka Realtime |
| AWS SQS | AWS SQS |
| MongoDB | MongoDB |
| PostgreSQL | Postgres |

## Impact
- ✅ Users can now easily distinguish between different trigger types
- ✅ No more generic "RealtimeTrigger" or "Trigger" cards
- ✅ Plugin context is immediately visible
- ✅ Backward compatible - falls back gracefully for unknown patterns
- ✅ Uses existing `RegisteredPlugin.title()` metadata
- ✅ No new dependencies required

## Files Modified
1. `webserver/src/main/java/io/kestra/webserver/controllers/api/PluginController.java`
2. `ui/src/components/admin/triggers/triggerCatalog.ts`
3. `ui/src/components/admin/triggers/triggerCatalog.test.ts` (new)

## Testing Instructions
1. Build the project with Java 21+
2. Start Kestra
3. Navigate to Tenant → Triggers → Add
4. Observe trigger cards now display meaningful names with plugin context
5. Verify all trigger types are clearly distinguishable
