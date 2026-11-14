import {appendFileSync, mkdirSync, writeFileSync} from "node:fs"
import {dirname, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import type {FullConfig, FullResult, Reporter, Suite, TestCase} from "@playwright/test/reporter"

type Outcome = "flaky" | "unexpected"
type Attempt = { retry: number; status: string; durationMs: number }
type TestSummary = { title: string; file: string; line: number; outcome: Outcome; attempts: Attempt[] }
type Report = {
    generatedAt: string
    run: { id: string | null; number: string | null; sha: string | null; ref: string | null }
    status: string
    totalTests: number
    executedTests: number
    skippedTests: number
    flakyTestCount: number
    flakinessRate: number | null
    unexpectedOutcomeCount: number
    retryAttempts: number
    flakyTests: TestSummary[]
    unexpectedTests: TestSummary[]
}

const DEFAULT_REPORT_DIRECTORY = resolve(dirname(fileURLToPath(import.meta.url)), "playwright-report")

function summarize(test: TestCase, outcome: Outcome): TestSummary {
    return {
        title: test.titlePath().filter(Boolean).join(" › "),
        file: test.location.file,
        line: test.location.line,
        outcome,
        attempts: test.results.map((result) => ({
            retry: result.retry,
            status: result.status,
            durationMs: result.duration,
        })),
    }
}

function markdownCell(value: string): string {
    return value.replace(/\|/g, "\\|").replace(/\r?\n/g, " ")
}

function testTable(tests: TestSummary[]): string {
    if (tests.length === 0) {
        return "_None detected in this run._"
    }

    return [
        "| Test | Location | Attempt outcomes |",
        "| --- | --- | --- |",
        ...tests.map((test) => {
            const attempts = test.attempts.map((attempt) => attempt.status).join(" → ") || "not run"
            return "| " + markdownCell(test.title) + " | " + markdownCell(test.file + ":" + test.line) + " | " + markdownCell(attempts) + " |"
        }),
    ].join("\n")
}

function renderMarkdown(report: Report): string {
    const rate = report.flakinessRate === null ? "n/a" : report.flakinessRate.toFixed(1) + "%"
    const runId = report.run.id
    const serverUrl = process.env.GITHUB_SERVER_URL
    const repository = process.env.GITHUB_REPOSITORY
    const runUrl = runId && serverUrl && repository ? serverUrl + "/" + repository + "/actions/runs/" + runId : null
    const commit = report.run.sha ? report.run.sha.slice(0, 7) : null
    const runReference = runUrl ? "[" + (report.run.number ? "#" + report.run.number : runId) + "](" + runUrl + ")" : runId ?? "local run"

    return [
        "## E2E test flakiness",
        "",
        "- **Run:** " + runReference + (commit ? " · commit " + commit : ""),
        "- **Result:** " + report.status,
        "- **Tests:** " + report.executedTests + " executed, " + report.skippedTests + " skipped, " + report.totalTests + " total",
        "- **Flaky tests:** " + report.flakyTestCount + " (" + rate + ")",
        "- **Unexpected outcomes after retries:** " + report.unexpectedOutcomeCount,
        "- **Retry attempts:** " + report.retryAttempts,
        "",
        "A test is counted as flaky only when at least one attempt fails and a later attempt passes. Other unexpected outcomes are reported separately and are not counted as flaky.",
        "",
        "### Flaky tests (failed, then passed)",
        "",
        testTable(report.flakyTests),
        "",
        "### Unexpected outcomes (not counted as flaky)",
        "",
        testTable(report.unexpectedTests),
        "",
        "The matching JSON and Markdown reports are also written to the Playwright report directory and uploaded with the existing CI artifact.",
    ].join("\n")
}

export default class FlakinessReporter implements Reporter {
    private suite?: Suite
    private readonly reportDirectory: string

    constructor(options: {outputDirectory?: string} = {}) {
        this.reportDirectory = options.outputDirectory ? resolve(options.outputDirectory) : DEFAULT_REPORT_DIRECTORY
    }

    onBegin(_config: FullConfig, suite: Suite): void {
        this.suite = suite
    }

    onEnd(result: FullResult): void {
        if (!this.suite) {
            return
        }

        const tests = this.suite.allTests()
        const flakyTests = tests.filter((test) => test.outcome() === "flaky").map((test) => summarize(test, "flaky"))
        const unexpectedTests = tests.filter((test) => test.outcome() === "unexpected").map((test) => summarize(test, "unexpected"))
        const skippedTests = tests.filter((test) => test.outcome() === "skipped").length
        const executedTests = tests.length - skippedTests
        const retryAttempts = tests.reduce((total, test) => total + test.results.filter((attempt) => attempt.retry > 0).length, 0)

        const report: Report = {
            generatedAt: new Date().toISOString(),
            run: {
                id: process.env.GITHUB_RUN_ID ?? null,
                number: process.env.GITHUB_RUN_NUMBER ?? null,
                sha: process.env.GITHUB_SHA ?? null,
                ref: process.env.GITHUB_REF ?? null,
            },
            status: result.status,
            totalTests: tests.length,
            executedTests,
            skippedTests,
            flakyTestCount: flakyTests.length,
            flakinessRate: executedTests > 0 ? Number(((flakyTests.length / executedTests) * 100).toFixed(2)) : null,
            unexpectedOutcomeCount: unexpectedTests.length,
            retryAttempts,
            flakyTests,
            unexpectedTests,
        }
        const markdown = renderMarkdown(report)

        try {
            mkdirSync(this.reportDirectory, {recursive: true})
            writeFileSync(resolve(this.reportDirectory, "flakiness-report.json"), JSON.stringify(report, null, 2) + "\n", "utf8")
            writeFileSync(resolve(this.reportDirectory, "flakiness-report.md"), markdown + "\n", "utf8")
        } catch (error) {
            console.error("Could not write the E2E flakiness report:", error)
        }

        const summaryPath = process.env.GITHUB_STEP_SUMMARY
        try {
            if (summaryPath) {
                appendFileSync(summaryPath, markdown + "\n\n", "utf8")
            } else {
                console.log("\n" + markdown + "\n")
            }
        } catch (error) {
            console.error("Could not append the E2E flakiness report to the job summary:", error)
        }
    }
}
