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

    for (const report of testReports) {
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
