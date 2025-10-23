# Testing Guide: outputFromIteration() Function - Windows VS Code

This guide provides step-by-step instructions for building, testing, and running the new `outputFromIteration()` Pebble function in Kestra on Windows using VS Code.

## Table of Contents
1. [Prerequisites](#prerequisites)
2. [Environment Setup](#environment-setup)
3. [Building the Project](#building-the-project)
4. [Running Unit Tests](#running-unit-tests)
5. [Running Kestra Locally](#running-kestra-locally)
6. [Testing with Example Flows](#testing-with-example-flows)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software
- **Java 21** (JDK 21)
  - Download from: https://adoptium.net/ or https://www.oracle.com/java/technologies/downloads/
  - Verify installation: `java -version` in PowerShell/CMD
- **Node.js 20+** (for UI development)
  - Download from: https://nodejs.org/
  - Verify installation: `node --version`
- **VS Code**
  - Download from: https://code.visualstudio.com/
- **Git** for cloning the repository
  - Download from: https://git-scm.com/download/win
- **Docker Desktop** (optional, for running full Kestra stack)
  - Download from: https://www.docker.com/products/docker-desktop

### Recommended VS Code Extensions
- **Extension Pack for Java** (Microsoft)
- **Gradle for Java** (Microsoft)
- **YAML** (Red Hat)
- **Test Runner for Java** (Microsoft)

---

## Environment Setup

### 1. Set JAVA_HOME Environment Variable

**Option A: Using PowerShell (Temporary)**
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.x"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
```

**Option B: Using System Properties (Permanent)**
1. Open Start Menu → Search for "Environment Variables"
2. Click "Edit the system environment variables"
3. Click "Environment Variables..." button
4. Under "System variables", click "New..."
5. Variable name: `JAVA_HOME`
6. Variable value: `C:\Program Files\Eclipse Adoptium\jdk-21.0.x` (adjust path to your installation)
7. Edit the `Path` variable and add `%JAVA_HOME%\bin`
8. Click OK to save

### 2. Verify Java Installation
```powershell
java -version
# Should show: openjdk version "21.0.x"

javac -version
# Should show: javac 21.0.x
```

### 3. Clone the Repository (if not already done)
```powershell
git clone https://github.com/kestra-io/kestra.git
cd kestra
```

### 4. Open Project in VS Code
```powershell
code .
```

---

## Building the Project

### Option 1: Build Using Gradle Wrapper (Recommended)

**Windows PowerShell:**
```powershell
# Build the entire project (skip tests for faster build)
.\gradlew build -x test

# Or build with tests (takes longer)
.\gradlew build
```

**Windows CMD:**
```cmd
gradlew.bat build -x test
```

### Option 2: Build Only Core Module
```powershell
.\gradlew :core:build -x test
```

### Expected Output
```
BUILD SUCCESSFUL in 2m 30s
```

---

## Running Unit Tests

### Test the New Function Specifically

**PowerShell:**
```powershell
# Run only the OutputFromIterationFunction tests
.\gradlew :core:test --tests "OutputFromIterationFunctionTest"

# Run with verbose output
.\gradlew :core:test --tests "OutputFromIterationFunctionTest" --info
```

**CMD:**
```cmd
gradlew.bat :core:test --tests "OutputFromIterationFunctionTest"
```

### View Test Results
Test reports are generated at:
```
core\build\reports\tests\test\index.html
```

Open this file in your browser to see detailed test results.

### Run All Pebble Function Tests
```powershell
.\gradlew :core:test --tests "*pebble.functions*"
```

---

## Running Kestra Locally

### Option 1: Using Gradle (Development Mode)

**PowerShell:**
```powershell
# Run Kestra in local mode
.\gradlew runLocal
```

This will:
- Start Kestra server on http://localhost:8080
- Use in-memory database (H2)
- Auto-reload on code changes (if configured)

**Wait for the server to start:**
Look for this message in the console:
```
Startup completed in XXXXms. Server Running: http://localhost:8080
```

### Option 2: Using Docker (Full Stack)

**PowerShell:**
```powershell
# Build the executable JAR first
.\gradlew executableJar

# Then run with Docker
docker run --rm -it -p 8080:8080 --user=root `
  -v ${PWD}/build/executable:/app `
  -v /var/run/docker.sock:/var/run/docker.sock `
  kestra/kestra:latest server local
```

**Note:** On Windows, Docker requires WSL 2 or Hyper-V.

### Option 3: Build and Run Standalone JAR

```powershell
# Build the executable JAR
.\gradlew executableJar

# The JAR will be created at:
# build\executable\kestra-VERSION.jar

# Run it
java -jar build\executable\kestra-*.jar server local
```

---

## Testing with Example Flows

### 1. Access the Kestra UI
Open your browser and navigate to: **http://localhost:8080**

### 2. Create a New Flow

**Option A: Using the UI**
1. Click on "Flows" in the left sidebar
2. Click "+ Create" button
3. Copy one of the example YAML files from the `examples/` folder
4. Paste into the editor
5. Click "Save"

**Option B: Using the Examples**

Navigate to the examples folder:
```
cd examples
```

Copy one of the example flows:
- `foreach_previous_iteration.yaml` - Basic example
- `foreach_cumulative_sum.yaml` - Practical cumulative sum
- `foreach_compare_iterations.yaml` - Comparison logic
- `foreach_chain_processing.yaml` - Complex chain processing

### 3. Execute the Flow
1. Click "Execute" button in the top right
2. The flow will run and show execution status
3. Click on each task to see the output
4. Verify that the `outputFromIteration()` function correctly retrieves previous iteration values

### 4. Expected Results

**For `foreach_previous_iteration.yaml`:**
- Iteration 0: Should show "This is the first iteration"
- Iteration 1: Should show output from iteration 0
- Iteration 2: Should show output from iteration 1
- And so on...

**For `foreach_cumulative_sum.yaml`:**
- Iteration 0: 10
- Iteration 1: 30 (10 + 20)
- Iteration 2: 60 (30 + 30)
- Iteration 3: 100 (60 + 40)
- Iteration 4: 150 (100 + 50)

---

## Debugging in VS Code

### 1. Create Launch Configuration

Create `.vscode/launch.json`:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Debug Kestra Server",
      "request": "launch",
      "mainClass": "io.kestra.cli.App",
      "args": ["server", "local"],
      "projectName": "cli",
      "cwd": "${workspaceFolder}",
      "env": {
        "MICRONAUT_ENVIRONMENTS": "override"
      }
    },
    {
      "type": "java",
      "name": "Debug OutputFromIterationFunctionTest",
      "request": "launch",
      "mainClass": "org.junit.platform.console.ConsoleLauncher",
      "args": [
        "--select-class",
        "io.kestra.core.runners.pebble.functions.OutputFromIterationFunctionTest"
      ],
      "projectName": "core",
      "cwd": "${workspaceFolder}"
    }
  ]
}
```

### 2. Set Breakpoints
1. Open `OutputFromIterationFunction.java`
2. Click in the left margin (line number area) to set breakpoints
3. Press F5 or click "Run" → "Start Debugging"

### 3. Debug Test
1. Select "Debug OutputFromIterationFunctionTest" from the debug dropdown
2. Press F5 to start debugging
3. The debugger will stop at your breakpoints

---

## Troubleshooting

### Issue: "JAVA_HOME is not set"
**Solution:**
```powershell
# In PowerShell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.x"
```

### Issue: "gradlew: command not found"
**Solution:**
Use the full gradlew command:
```powershell
.\gradlew build  # PowerShell
gradlew.bat build  # CMD
```

### Issue: Build fails with "incompatible Java version"
**Solution:**
Ensure Java 21 is installed and JAVA_HOME points to it:
```powershell
java -version  # Must be 21.x
```

### Issue: "Port 8080 already in use"
**Solution:**
Find and kill the process using port 8080:
```powershell
# Find process
netstat -ano | findstr :8080

# Kill process (replace PID with actual process ID)
taskkill /PID <PID> /F
```

Or use a different port:
```powershell
.\gradlew runLocal -Dmicronaut.server.port=8081
```

### Issue: Tests fail with "Cannot find symbol"
**Solution:**
Clean and rebuild:
```powershell
.\gradlew clean build
```

### Issue: Docker not starting on Windows
**Solution:**
1. Ensure Docker Desktop is running
2. Enable WSL 2 integration in Docker Desktop settings
3. Or use Hyper-V backend

### Issue: Frontend not loading
**Solution:**
```powershell
cd ui
npm install
npm run dev
```

---

## Advanced Testing

### Performance Testing
```powershell
# Run with larger datasets
.\gradlew :jmh-benchmarks:jmh -Pinclude=".*OutputFromIteration.*"
```

### Integration Testing
```powershell
# Run integration tests
.\gradlew :e2e-tests:test
```

### Code Coverage
```powershell
# Generate coverage report
.\gradlew jacocoTestReport

# View report at:
# build\reports\jacoco\test\html\index.html
```

---

## Verification Checklist

- [ ] Java 21 installed and JAVA_HOME set
- [ ] Project builds successfully without errors
- [ ] All unit tests pass (OutputFromIterationFunctionTest)
- [ ] Kestra server starts successfully
- [ ] Can access UI at http://localhost:8080
- [ ] Example flows execute successfully
- [ ] outputFromIteration() function works with:
  - [ ] First iteration (returns error or handles gracefully)
  - [ ] Middle iterations (accesses previous values)
  - [ ] Last iteration (accesses all previous values)
  - [ ] Conditional logic (if/else for iteration 0)
  - [ ] Arithmetic operations (cumulative sum)
  - [ ] Out of bounds error handling

---

## Summary of Testing Steps

```powershell
# Quick testing workflow
cd kestra

# 1. Build the project
.\gradlew build -x test

# 2. Run unit tests
.\gradlew :core:test --tests "OutputFromIterationFunctionTest"

# 3. Start Kestra server
.\gradlew runLocal

# 4. Open browser to http://localhost:8080

# 5. Test with example flows in examples/ folder
```

---

## Additional Resources

- **Kestra Documentation:** https://kestra.io/docs
- **Pebble Template Engine:** https://pebbletemplates.io/
- **Java Debugging in VS Code:** https://code.visualstudio.com/docs/java/java-debugging
- **Gradle User Guide:** https://docs.gradle.org/current/userguide/userguide.html

---

## Support

If you encounter issues:
1. Check the troubleshooting section above
2. Review the logs in `logs/` directory
3. Check GitHub issues: https://github.com/kestra-io/kestra/issues
4. Join Kestra Slack: https://kestra.io/slack

---

**Last Updated:** 2025-10-23
**Feature:** outputFromIteration() Pebble function for accessing previous iteration values
