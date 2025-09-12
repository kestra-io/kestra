import {WorkingDir} from "../utilities/working-dir";
import {MarkdownString, summarizeJunitReport, TestReport,} from "./functions/summarize-junit-report";
import {parseJunitModuleReport} from "./functions/parse-junit-module-report";
import fg from "fast-glob";
import fs from "fs";
import {getJavaProjectNameFromBuildAbsolutePath} from "./functions/file-path-utils";

/**
 * parse files located at 'testReportsLocationPattern' and generate a summary in Markdown
 * @param workingDir
 * @param options
 */
export async function generateTestReportSummary(
    workingDir: WorkingDir,
    options?: {
        onlyErrors?: boolean;
        testReportsLocationPattern?: "**/build/test-results/junit/*.xml";
    },
): Promise<MarkdownString> {
    const onlyErrors = options?.onlyErrors ?? false;
    const pattern = options?.testReportsLocationPattern ?? "**/build/test-results/junit/*.xml";

    // Find matching report files under the provided working directory
    const junitXmlReportsFilenames = await fg.async(pattern, {
        cwd: workingDir,
        absolute: true,
        onlyFiles: true,
        dot: true,
        followSymbolicLinks: true,
    });

    // Parse each JUnit report into a module-level structure
    const moduleReports: TestReport[] = junitXmlReportsFilenames.map((file) => {
        const content = fs.readFileSync(file, "utf-8");
        return {
            projectName: getJavaProjectNameFromBuildAbsolutePath(file),
            projectReport: parseJunitModuleReport(content),
        };
    });

    // Summarize all parsed reports into a single Markdown string
    return summarizeJunitReport(moduleReports, {onlyErrors: onlyErrors}).markdownContent;
}
