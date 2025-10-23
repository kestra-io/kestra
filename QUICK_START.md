# Quick Start: Testing outputFromIteration() Function

## ✅ What's Been Implemented

The `outputFromIteration()` Pebble function has been implemented to access previous iteration values in **flat** ForEach loops.

### Working Features
- ✅ Access any iteration output by index
- ✅ Access previous iteration (`taskrun.iteration - 1`)
- ✅ Cumulative calculations (running sums, products, etc.)
- ✅ Comparison between iterations
- ✅ Conditional logic based on iteration index
- ✅ Clear error messages for invalid indices

### Current Limitation
- ⚠️ **Nested ForEach loops are NOT yet supported** - Parent traversal needs additional work
- ⚠️ **All example flows use flat (single-level) ForEach only**

---

## 🚀 Quick Testing Steps (Windows VS Code)

### Prerequisites
- Java 21 installed
- VS Code with Java extensions
- Git clone of Kestra repository

### Step 1: Build the Project
```powershell
# Navigate to kestra directory
cd kestra

# Build (this may take 5-10 minutes first time)
.\gradlew :core:build -x test
```

### Step 2: Run Unit Tests
```powershell
# Test the new function
.\gradlew :core:test --tests "OutputFromIterationFunctionTest"
```

### Step 3: Start Kestra Server
```powershell
# Run Kestra locally
.\gradlew runLocal

# Wait for: "Server Running: http://localhost:8080"
```

### Step 4: Test with Example Flow
1. Open browser: **http://localhost:8080**
2. Click "Flows" → "+ Create"
3. Copy this simple test flow:

```yaml
id: test_output_from_iteration
namespace: company.team

tasks:
  - id: simple_loop
    type: io.kestra.plugin.core.flow.ForEach
    values: [10, 20, 30, 40, 50]
    tasks:
      - id: show_previous
        type: io.kestra.plugin.core.debug.Return
        format: |
          Current: {{ taskrun.value }}
          Iteration: {{ taskrun.iteration }}
          {% if taskrun.iteration > 0 %}
          Previous: {{ outputFromIteration(outputs.simple_loop, taskrun.iteration - 1).value }}
          {% else %}
          (No previous iteration)
          {% endif %}
```

4. Click "Save" then "Execute"
5. Check the outputs - each iteration should show the previous value

### Expected Results
- **Iteration 0**: Current: 10, "(No previous iteration)"
- **Iteration 1**: Current: 20, Previous: 10
- **Iteration 2**: Current: 30, Previous: 20
- **Iteration 3**: Current: 40, Previous: 30
- **Iteration 4**: Current: 50, Previous: 40

---

## 📁 Files Created/Modified

### Backend Implementation
- `core/src/main/java/io/kestra/core/runners/pebble/functions/OutputFromIterationFunction.java` (NEW)
- `core/src/main/java/io/kestra/core/runners/pebble/Extension.java` (MODIFIED - function registration)

### Tests
- `core/src/test/java/io/kestra/core/runners/pebble/functions/OutputFromIterationFunctionTest.java` (NEW)

### Examples
- `examples/foreach_previous_iteration.yaml`
- `examples/foreach_cumulative_sum.yaml`
- `examples/foreach_compare_iterations.yaml`
- `examples/foreach_chain_processing.yaml`

### Documentation
- `TESTING_GUIDE_WINDOWS.md` - Complete testing guide
- `IMPLEMENTATION_SUMMARY.md` - Technical details
- `QUICK_START.md` - This file

---

##Function Signature

```pebble
outputFromIteration(outputs, iteration)
```

**Parameters:**
- `outputs`: The outputs map from a ForEach task (e.g., `outputs.my_task`)
- `iteration`: Integer index of the iteration to retrieve (0-based)

**Returns:** The output object from the specified iteration

**Throws:** PebbleException if iteration index is out of bounds or not found

---

## 💡 Usage Examples

### Example 1: Cumulative Sum
```yaml
- id: cumulative_sum
  type: io.kestra.plugin.core.flow.ForEach
  values: [5, 10, 15, 20]
  tasks:
    - id: calc_sum
      type: io.kestra.plugin.core.debug.Return
      format: |
        {% if taskrun.iteration == 0 %}
        {{ taskrun.value }}
        {% else %}
        {{ taskrun.value + outputFromIteration(outputs.cumulative_sum, taskrun.iteration - 1).value | int }}
        {% endif %}
```

**Result:** 5, 15, 30, 50

### Example 2: Detect Changes
```yaml
- id: temperature_monitor
  type: io.kestra.plugin.core.flow.ForEach
  values: [72, 75, 78, 91, 95]
  tasks:
    - id: check_change
      type: io.kestra.plugin.core.debug.Return
      format: |
        {% if taskrun.iteration > 0 %}
        {% set prev = outputFromIteration(outputs.temperature_monitor, taskrun.iteration - 1).value | int %}
        {% set diff = taskrun.value - prev %}
        {% if diff > 10 %}⚠️ ALERT: Spike detected!{% endif %}
        {% endif %}
```

### Example 3: Conditional Logic
```yaml
format: |
  {% if taskrun.iteration > 0 %}
  Previous result: {{ outputFromIteration(outputs.my_task, taskrun.iteration - 1) }}
  {% else %}
  This is the first iteration
  {% endif %}
```

---

## ⚠️ Important Notes

### Iteration Indexing
- Iterations are **0-based** (first iteration is 0, not 1)
- Use `taskrun.iteration - 1` to access the previous iteration
- Always check `if taskrun.iteration > 0` before accessing previous iterations

### Error Handling
The function will throw an error if:
- Iteration index is negative
- Iteration index doesn't exist in outputs
- Required arguments are missing

**Best Practice:** Always use conditional checks:
```pebble
{% if taskrun.iteration > 0 %}
  {{ outputFromIteration(outputs.task, taskrun.iteration - 1) }}
{% endif %}
```

### Nested ForEach (Not Supported)
```yaml
# ❌ This will NOT work currently:
- id: outer
  type: io.kestra.plugin.core.flow.ForEach
  values: [1, 2, 3]
  tasks:
    - id: inner
      type: io.kestra.plugin.core.flow.ForEach
      values: [a, b, c]
      tasks:
        - id: nested_task
          # outputFromIteration() won't work correctly here yet
```

---

## 🐛 Troubleshooting

### Build Fails
```powershell
# Clean and rebuild
.\gradlew clean build -x test
```

### Tests Fail
```powershell
# Run with verbose output
.\gradlew :core:test --tests "OutputFromIterationFunctionTest" --info
```

### Server Won't Start
```powershell
# Check if port 8080 is in use
netstat -ano | findstr :8080

# Kill the process if needed
taskkill /PID <PID> /F
```

### "Iteration not found" Error in Flow
- Check that you're using the correct task ID in `outputs.taskId`
- Verify iteration index is within bounds (0 to N-1)
- Make sure you're checking `if taskrun.iteration > 0` for previous access

---

## 📊 Test Checklist

Before considering the feature complete, verify:

- [ ] Project builds successfully
- [ ] Unit tests pass
- [ ] Kestra server starts
- [ ] Example flow executes without errors
- [ ] First iteration handles "no previous" correctly
- [ ] Middle iterations access previous values correctly
- [ ] Last iteration can access all previous values
- [ ] Cumulative sum example produces correct results
- [ ] Out-of-bounds access shows helpful error message

---

## 🎯 Next Steps

### For Testing
1. Complete the Quick Testing Steps above
2. Try all 4 example flows in `examples/` directory
3. Create your own test case for your specific use case
4. Report any issues or unexpected behavior

### For Production Use
1. All tests must pass
2. Verify examples work as expected
3. Consider nested ForEach requirements for your use case
4. If nested support is needed, additional development will be required

### For Future Enhancement
- Nested ForEach support (requires parent iteration key resolution)
- Retry suffix handling in iteration keys  
- Integration tests with real Kestra executions
- Performance optimization for large iteration counts

---

## 📞 Support

If you encounter issues:
1. Check the troubleshooting section
2. Review `TESTING_GUIDE_WINDOWS.md` for detailed instructions
3. Check logs in `logs/` directory  
4. Refer to `IMPLEMENTATION_SUMMARY.md` for technical details

---

**Created:** 2025-10-23  
**Feature:** outputFromIteration() Pebble function  
**Status:** Ready for testing (flat ForEach loops only)  
**Nested Support:** Planned for future iteration
