import { JUnitModuleReport } from "./parse-junit-module-report";

export type MarkdownString = string;

export interface TestReport {
    projectName: string;
    projectReport: JUnitModuleReport;
}

export interface TestReportSummary {
    hasErrors: boolean;
    markdownContent: MarkdownString;
}

export function summarizeJunitReport(
    testReports: TestReport[],
    options?: { onlyErrors: boolean },
): TestReportSummary {
    const onlyErrors = options?.onlyErrors ?? false;

    const testReportQuickSummaryRows: string[] = [];
    const testReportDetailsRows: string[] = [];
    const testReportErrorLogs: string[] = [];
    let hasErrors = false;

    const mergedReports = mergeSameProjectReports(testReports);
    for (const report of mergedReports) {
        const project = report.projectName;
        const projectReport: JUnitModuleReport = report.projectReport;
        testReportQuickSummaryRows.push(
            `| ${escapePipe(report.projectName)} | ${escapePipe(mapStatusToEmoji(projectReport.status))} | ${escapePipe(projectReport.success)} | ${escapePipe(projectReport.skipped)} | ${projectReport.errors + projectReport.failures} |`,
        );

        for (const testsuite of projectReport.testsuites) {
            for (const testcase of testsuite.testcases) {
                const name = testcase.name ?? "";
                const duration = safeNum(testcase.time);
                const failed = testcase.status === "failed" || testcase.status === "error";
                if (failed) hasErrors = true;
                if (onlyErrors) {
                    // then only print errors, and details like logs
                    if (failed) {
                        const message = testcase.message ?? "";
                        const details = testcase.details ? "\n\n" + testcase.details : "";

                        testReportErrorLogs.push(
                            `${escapePipe(project)} > ${escapePipe(testsuite.name)} > ${escapePipe(name)} ${mapStatusToEmoji(testcase.status)} in ${duration}:
                                    \n${codeBlock(message + details)}`,
                        );
                    }
                } else {
                    testReportDetailsRows.push(
                        `| ${escapePipe(project)} | ${escapePipe(testsuite.name)} | ${escapePipe(name)} | ${mapStatusToEmoji(testcase.status)} | ${duration} | ${escapePipe(truncate(testcase.message ?? "", 200))} |`,
                    );
                }
            }
        }
    }
    let markdownContent = "## Tests report quick summary:";
    const totalTests = testReports.map(r => r.projectReport.tests).reduce((a,b) => a+b);
    const totalSuccess = testReports.map(r => r.projectReport.success).reduce((a,b) => a+b);
    const totalSkipped = testReports.map(r => r.projectReport.skipped).reduce((a,b) => a+b);
    const totalErrors = testReports.map(r => r.projectReport.failures + r.projectReport.errors).reduce((a,b) => a+b);
    markdownContent = markdownContent + `\ntotals > tests: ${totalTests}, success: ${totalSuccess}, skipped: ${totalSkipped}, failed: ${totalErrors}\n`;
    markdownContent =
        markdownContent +
        `\n| Project | Status | Success | Skipped | Failed |\n|---|---|---|---|---|`;
    markdownContent = markdownContent + "\n" + [...testReportQuickSummaryRows].join("\n");
    if (testReportDetailsRows.length > 0) {
        markdownContent = markdownContent + "\n\n" + "## Tests report details:";
        const header = `| Project | Suite | Test | Status | Duration (s) | Message |\n|---|---|---|---|---:|---|`;
        markdownContent = markdownContent + "\n" + [header, ...testReportDetailsRows].join("\n");
    }
    if (testReportErrorLogs.length > 0) {
        markdownContent = markdownContent + "\n## Failed tests:";
        markdownContent = markdownContent + "\n" + [...testReportErrorLogs].join("\n");
    }

    return { hasErrors, markdownContent };

    // merge reports that share the same projectName by concatenating testsuites
    function mergeSameProjectReports(reports: TestReport[]): TestReport[] {
        const byProject = new Map<string, JUnitModuleReport>();

        for (const r of reports) {
            const key = r.projectName;
            const existing = byProject.get(key);
            if (!existing) {
                // clone a shallow copy so we don't mutate the original
                const cloned: JUnitModuleReport = {
                    ...r.projectReport,
                    testsuites: [...r.projectReport.testsuites],
                } as JUnitModuleReport;
                computeModuleAggregates(cloned);
                byProject.set(key, cloned);
            } else {
                // concatenate testsuites and recompute aggregates
                existing.testsuites = [...existing.testsuites, ...r.projectReport.testsuites];
                computeModuleAggregates(existing);
            }
        }

        // rebuild TestReport array
        return Array.from(byProject.entries()).map(([projectName, projectReport]) => ({
            projectName,
            projectReport,
        }));
    }

    // recompute success/skip/error/failure counts and overall status from testcases
    function computeModuleAggregates(moduleReport: JUnitModuleReport): void {
        let success = 0;
        let skipped = 0;
        let errors = 0;
        let failures = 0;

        for (const suite of moduleReport.testsuites) {
            for (const tc of suite.testcases) {
                switch (tc.status) {
                    case "success":
                        success++; break;
                    case "skipped":
                        skipped++; break;
                    case "error":
                        errors++; break;
                    case "failed":
                        failures++; break;
                }
            }
        }

        const total = success + skipped + errors + failures;
        // update known aggregate fields if present on the type
        moduleReport.success = success;
        moduleReport.skipped = skipped;
        moduleReport.errors = errors;
        moduleReport.failures = failures;
        if ("tests" in moduleReport) {
            moduleReport.tests = total;
        }

        // status rules: all skipped => skipped; any error => error; any failed => failed; else success
        let status: "success" | "failed" | "error" | "skipped";
        if (total > 0 && skipped === total) status = "skipped";
        else if (errors > 0) status = "error";
        else if (failures > 0) status = "failed";
        else status = "success";
        moduleReport.status = status;
    }

    // helpers scoped below
    function escapePipe(s: string | number | undefined): string {
        const str = s == null ? "" : String(s);
        // escape pipe and newlines for markdown table cells
        return str.replace(/\|/g, "\\|").replace(/\r?\n/g, " ↵ ");
    }

    function codeBlock(s: string | number | undefined): string {
        const str = s == null ? "" : String(s);
        return `\`\`\`\n${str}\n\`\`\`\n`;
    }

    function truncate(s: string, max: number): string {
        return s && s.length > max ? s.slice(0, max - 1) + "…" : s || "";
    }

    function safeNum(v: number | undefined): string {
        if (v === undefined || v === null) return "";
        const n = typeof v === "number" ? v : Number(String(v));
        if (Number.isFinite(n)) return n.toFixed(3).replace(/\.000$/, "");
        return String(v);
    }

    function mapStatusToEmoji(status: "success" | "failed" | "error" | "skipped"): string {
        switch (status) {
            case "failed":
                return "failed ❌";
            case "error":
                return "error ❌";
            case "skipped":
                return "skipped ⏭️";
            case "success":
                return "success ✅";
            default:
                throw new Error("Unhandled case");
        }
    }
}
