import { promises as fs } from "node:fs";
import { XMLParser } from "fast-xml-parser";

export type JUnitModuleReport = {
    suites: number;
    tests: number;
    failures: number;
    errors: number;
    skipped: number;
    success: number;
    status: "success" | "failed" | "error" | "skipped";
    time: number; // total duration in seconds
    testsuites: Array<JunitTestSuite>;
};

export interface JunitTestSuite {
    name?: string;
    tests: number;
    failures: number;
    errors: number;
    skipped: number;
    success: number;
    status: "success" | "failed" | "error" | "skipped";
    time: number;
    testcases: Array<JunitTestCase>;
}

export interface JunitTestCase {
    classname?: string;
    name: string;
    time?: number;
    status: "success" | "failed" | "error" | "skipped";
    message?: string;
    type?: string;
    details?: string;
}

// for more info on the Junit test report format = https://github.com/testmoapp/junitxml
export function parseJunitModuleReport(xml: string): JUnitModuleReport {
    const parser = new XMLParser({
        ignoreAttributes: false,
        attributeNamePrefix: "",
        allowBooleanAttributes: true,
        parseAttributeValue: true,
        trimValues: false,
    });

    const obj = parser.parse(xml);

    // JUnit can be either <testsuites> or a single <testsuite>
    const rawSuites = obj?.testsuites?.testsuite ?? obj?.testsuite ?? [];

    // eslint-disable-next-line  @typescript-eslint/no-explicit-any
    const suites = toArray<any>(rawSuites);

    const report: JUnitModuleReport = {
        suites: suites.length,
        tests: 0,
        failures: 0,
        errors: 0,
        skipped: 0,
        success: 0,
        status: "success",
        time: 0,
        testsuites: [],
    };

    for (const s of suites) {
        const name: string | undefined = s.name;

        // Attributes may exist on the suite OR we may need to infer from testcases
        // eslint-disable-next-line  @typescript-eslint/no-explicit-any
        const testcases = toArray<any>(s.testcase ?? []);

        const suiteCounts = {
            tests: numeric(s.tests, testcases.length),
            failures: numeric(s.failures, 0),
            errors: numeric(s.errors, 0),
            skipped: numeric(s.skipped, 0),
            time: numeric(s.time, sum(testcases.map((tc) => numeric(tc.time, 0)))),
        };

        // If suite attributes missing, infer from testcases
        if (
            !isFiniteNumber(s.failures) ||
            !isFiniteNumber(s.errors) ||
            !isFiniteNumber(s.skipped)
        ) {
            let f = 0,
                e = 0,
                sk = 0;
            for (const tc of testcases) {
                if (hasKey(tc, "failed")) f += toArray(tc.failed).length;
                if (hasKey(tc, "error")) e += toArray(tc.error).length;
                if (hasKey(tc, "skipped")) sk += toArray(tc.skipped).length || 1; // some producers put empty <skipped/>
            }
            if (!isFiniteNumber(suiteCounts.failures)) suiteCounts.failures = f;
            if (!isFiniteNumber(suiteCounts.errors)) suiteCounts.errors = e;
            if (!isFiniteNumber(suiteCounts.skipped)) suiteCounts.skipped = sk;
        }

        const successCount =
            suiteCounts.tests - suiteCounts.errors - suiteCounts.failures - suiteCounts.skipped;

        let suiteStatus: "success" | "failed" | "error" | "skipped" = "success";
        if (suiteCounts.skipped === suiteCounts.tests) {
            suiteStatus = "skipped";
        } else if (suiteCounts.errors > 0) {
            suiteStatus = "error";
        } else if (suiteCounts.failures > 0) {
            suiteStatus = "failed";
        }

        const suiteDetail: JunitTestSuite = {
            name,
            tests: suiteCounts.tests,
            failures: suiteCounts.failures,
            errors: suiteCounts.errors,
            skipped: suiteCounts.skipped,
            success: successCount,
            status: suiteStatus,
            time: suiteCounts.time,
            testcases: [],
        };

        // Collect failed tests and build suiteDetail.testcases
        for (const tc of testcases) {
            const classname: string | undefined = tc.classname;
            const nameTc: string = tc.name;
            const time: number | undefined = isFiniteNumber(tc.time) ? Number(tc.time) : undefined;

            // Determine status
            if (tc.failure) {
                suiteDetail.testcases.push({
                    classname,
                    name: nameTc,
                    time,
                    status: "failed",
                    message: tc.failure.message,
                    type: tc.failure.type,
                    details: textContent(tc.failure),
                });
            } else if (tc.error) {
                suiteDetail.testcases.push({
                    classname,
                    name: nameTc,
                    time,
                    status: "error",
                    message: tc.error.message,
                    type: tc.error.message.type,
                    details: textContent(tc.error),
                });
            } else if (tc.skipped) {
                suiteDetail.testcases.push({
                    classname,
                    name: nameTc,
                    time,
                    status: "skipped",
                    message: tc.skipped.message,
                    details: textContent(tc.skipped),
                });
            } else {
                // success test
                suiteDetail.testcases.push({
                    classname,
                    name: nameTc,
                    time,
                    status: "success",
                });
            }
        }

        report.tests += suiteCounts.tests;
        report.failures += suiteCounts.failures;
        report.errors += suiteCounts.errors;
        report.skipped += suiteCounts.skipped;
        report.success += suiteDetail.success;
        report.time += suiteCounts.time;

        report.testsuites.push(suiteDetail);
    }

    if (report.skipped === report.tests) {
        report.status = "skipped";
    } else if (report.errors > 0) {
        report.status = "error";
    } else if (report.failures > 0) {
        report.status = "failed";
    } else {
        report.status = "success";
    }

    return report;
}

/**
 * Convenience: parse a file from disk.
 */
export async function summarizeJunitReportFromFile(filePath: string): Promise<JUnitModuleReport> {
    const xml = await fs.readFile(filePath, "utf8");
    return parseJunitModuleReport(xml);
}

// -------------------- helpers --------------------

function toArray<T>(v: T | T[] | undefined | null): T[] {
    if (v == null) return [];
    return Array.isArray(v) ? v : [v];
}

function numeric<T>(value: T, fallback = 0): number {
    const n = Number(value as unknown);
    return Number.isFinite(n) ? n : fallback;
}

function sum(nums: number[]): number {
    return nums.reduce((a, b) => a + b, 0);
}

function isFiniteNumber(v: unknown): v is number {
    const n = Number(v);
    return Number.isFinite(n);
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === "object" && value !== null;
}

// Some producers put the text of <failed> / <error> inside `#text` or as the value itself.
function textContent(node: unknown): string | undefined {
    if (node == null) return undefined;
    if (typeof node === "string") return node;
    if (isRecord(node) && typeof node["#text"] === "string") {
        return node["#text"] as string;
    }
    return undefined;
}

function hasKey<O>(obj: O, key: PropertyKey): key is keyof O {
    return obj != null && Object.prototype.hasOwnProperty.call(obj, key);
}
