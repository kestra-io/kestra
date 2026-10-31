import assert from "node:assert/strict"
import {mkdtempSync, readFileSync, rmSync, writeFileSync} from "node:fs"
import {tmpdir} from "node:os"
import {join} from "node:path"
import {test} from "node:test"
import FlakinessReporter from "./flakiness-reporter.ts"

function fakeTest(title, outcome, statuses) {
    return {
        titlePath: () => ["sample.spec.ts", title],
        location: {file: "e2e/sample.spec.ts", line: 12, column: 1},
        outcome: () => outcome,
        results: statuses.map((status, retry) => ({status, retry, duration: 10 + retry})),
    }
}

test("reports retry-passed tests as flaky and persistent failures separately", () => {
    const temporaryDirectory = mkdtempSync(join(tmpdir(), "kestra-flakiness-report-"))
    const reportDirectory = join(temporaryDirectory, "report")
    const summaryPath = join(temporaryDirectory, "step-summary.md")
    const previousSummaryPath = process.env.GITHUB_STEP_SUMMARY
    writeFileSync(summaryPath, "", "utf8")
    process.env.GITHUB_STEP_SUMMARY = summaryPath

    try {
        const tests = [
            fakeTest("passes on retry", "flaky", ["failed", "passed"]),
            fakeTest("still fails", "unexpected", ["failed", "failed", "failed"]),
            fakeTest("passes first time", "expected", ["passed"]),
            fakeTest("is skipped", "skipped", []),
        ]
        const reporter = new FlakinessReporter({outputDirectory: reportDirectory})
        reporter.onBegin({}, {allTests: () => tests})
        reporter.onEnd({status: "failed"})

        const report = JSON.parse(readFileSync(join(reportDirectory, "flakiness-report.json"), "utf8"))
        const markdown = readFileSync(summaryPath, "utf8")
        assert.equal(report.totalTests, 4)
        assert.equal(report.executedTests, 3)
        assert.equal(report.skippedTests, 1)
        assert.equal(report.flakyTestCount, 1)
        assert.equal(report.flakinessRate, 33.33)
        assert.equal(report.unexpectedOutcomeCount, 1)
        assert.equal(report.retryAttempts, 3)
        assert.deepEqual(report.flakyTests[0].attempts.map((attempt) => attempt.status), ["failed", "passed"])
        assert.match(markdown, /Flaky tests \(failed, then passed\)/)
        assert.match(markdown, /Unexpected outcomes \(not counted as flaky\)/)
        assert.match(markdown, /sample\.spec\.ts › passes on retry/)
        assert.match(readFileSync(join(reportDirectory, "flakiness-report.md"), "utf8"), /## E2E test flakiness/)
    } finally {
        if (previousSummaryPath === undefined) {
            delete process.env.GITHUB_STEP_SUMMARY
        } else {
            process.env.GITHUB_STEP_SUMMARY = previousSummaryPath
        }
        rmSync(temporaryDirectory, {recursive: true, force: true})
    }
})
