# Implementation Summary: outputFromIteration() Function

## Feature Overview
The `outputFromIteration()` Pebble function allows workflows to access outputs from specific iterations in ForEach loops, enabling:
- Accessing previous iteration values
- Cumulative calculations (e.g., running sums)
- Comparison between current and previous iterations  
- Conditional logic based on iteration history

## Implementation Details

### Files Modified/Created
1. **core/src/main/java/io/kestra/core/runners/pebble/functions/OutputFromIterationFunction.java** (NEW)
   - Main function implementation
   - Handles both List and Map output structures
   - Provides clear error messages for out-of-bounds access

2. **core/src/main/java/io/kestra/core/runners/pebble/Extension.java** (MODIFIED)
   - Registered `outputFromIteration` function in the Pebble extension

3. **core/src/test/java/io/kestra/core/runners/pebble/functions/OutputFromIterationFunctionTest.java** (NEW)
   - Comprehensive unit tests covering:
     - List-based outputs
     - Map-based outputs with string keys
     - Error handling (out of bounds, missing arguments)
     - Conditional access patterns
     - Previous iteration access

4. **examples/** (NEW - 4 example flows)
   - `foreach_previous_iteration.yaml` - Basic usage
   - `foreach_cumulative_sum.yaml` - Cumulative sum example
   - `foreach_compare_iterations.yaml` - Iteration comparison
   - `foreach_chain_processing.yaml` - Complex chained processing

5. **TESTING_GUIDE_WINDOWS.md** (NEW)
   - Complete step-by-step guide for testing on Windows with VS Code
   - Includes prerequisites, build steps, debugging instructions
   - Troubleshooting section

## Key Design Decisions

### 1. Iteration Lookup Strategy
- **Original Issue**: Initial implementation used `Map.values()` which doesn't guarantee iteration order
- **Fix**: Changed to lookup by string key using `String.valueOf(iteration)`
- **Rationale**: Kestra's ForEach outputs are keyed by iteration index as strings ("0", "1", "2", etc.)

### 2. Argument Design
- **Function Signature**: `outputFromIteration(outputs, iteration)`
- **outputs**: The outputs map/list from the ForEach task
- **iteration**: Integer or string representing the iteration index (0-based)

### 3. Error Handling
- Validates both arguments are present
- Checks for negative indices
- Provides clear error messages when iteration key is not found
- Shows available keys/indices in error messages

## Current Limitations

### Nested ForEach Support
**Status**: Not fully supported in current implementation

**Reason**: Parent traversal requires understanding how Kestra keys nested iteration outputs. The current parent descent logic needs enhancement to properly navigate nested output structures.

**Workaround**: For nested loops, consider flattening the structure or using intermediate variables.

**Future Enhancement**: Will be addressed once we validate the iteration key structure in nested scenarios through integration testing.

## Usage Patterns

### Pattern 1: Access Previous Iteration
```yaml
{% if taskrun.iteration > 0 %}
Previous value: {{ outputFromIteration(outputs.my_task, taskrun.iteration - 1) }}
{% endif %}
```

### Pattern 2: Cumulative Calculation
```yaml
{% if taskrun.iteration == 0 %}
{{ taskrun.value }}
{% else %}
{{ taskrun.value + outputFromIteration(outputs.my_task, taskrun.iteration - 1).sum | int }}
{% endif %}
```

### Pattern 3: Comparison Logic
```yaml
{% if taskrun.iteration > 0 %}
{% set prev = outputFromIteration(outputs.my_task, taskrun.iteration - 1).value | int %}
{% set curr = taskrun.value | int %}
{% if curr > prev %}Increasing{% else %}Decreasing{% endif %}
{% endif %}
```

## Testing Strategy

### Unit Tests
- ✅ List-based outputs
- ✅ Map-based outputs with string keys  
- ✅ Negative index handling
- ✅ Out of bounds handling
- ✅ Missing argument handling
- ✅ Conditional access patterns
- ⚠️ Nested ForEach contexts (requires integration testing)

### Integration Tests (Recommended Next Steps)
- Test with actual ForEach task executions
- Validate iteration key structure in real workflows
- Test nested ForEach scenarios
- Test with retry mechanisms

### Manual Testing
- Run example flows in local Kestra instance
- Verify outputs match expected values
- Test edge cases (empty loops, single iteration)

## Build and Test Instructions

### Quick Build
```powershell
# Windows PowerShell
.\gradlew :core:build -x test

# Run only the new function tests
.\gradlew :core:test --tests "OutputFromIterationFunctionTest"
```

### Run Kestra Locally
```powershell
.\gradlew runLocal
# Access UI at http://localhost:8080
```

### Test with Examples
1. Start Kestra server
2. Navigate to http://localhost:8080
3. Create a new flow
4. Copy contents from `examples/foreach_cumulative_sum.yaml`
5. Execute and verify outputs

## Success Criteria

- [x] Function implementation created
- [x] Function registered in Extension
- [x] Comprehensive unit tests written
- [x] Example flows created
- [x] Testing guide documented
- [ ] Code compiles successfully (requires Gradle build completion)
- [ ] All unit tests pass
- [ ] Example flows execute successfully in Kestra
- [ ] Documentation reviewed and approved

## Next Steps for Production

1. **Complete Build**: Finish Gradle compilation to resolve LSP errors
2. **Run Tests**: Execute unit tests to verify all test cases pass
3. **Integration Testing**: Test with real Kestra ForEach executions
4. **Nested ForEach**: Enhance parent traversal logic if nested support is required
5. **Retry Handling**: Add support for retry suffixes in iteration keys
6. **Documentation**: Update official Kestra documentation
7. **PR Review**: Submit for code review with maintainers

## Related GitHub Issue
- Feature Request: Access previous iteration values in ForEach loops
- Proposed Solution: Pebble function approach (vs. direct array indexing)
- Function Name: `outputFromIteration()` (vs. original suggestion `valueFromIteration()`)

## Contributors
- Implementation: Feature development based on community request
- Review: Architect feedback incorporated for Map ordering fix
