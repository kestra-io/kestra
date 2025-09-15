import {commentPR} from "../github-api";
import {WorkingDir} from "../utilities/working-dir";
import {generateTestReportSummary} from "./generate-test-report-summary";
import {strict as assert} from 'assert';

export async function exportTestReportSummary(workingDir: WorkingDir, options?: {
    onlyErrors?: boolean,
    githubContext?: { token: string, owner: string, repo: string, prNumber: number }
}) {
    const report = await generateTestReportSummary(workingDir, {onlyErrors: options?.onlyErrors})
    if (options?.githubContext) {
        assert.ok(options.githubContext.token, "github token is mandatory");
        assert.ok(options.githubContext.owner);
        assert.ok(options.githubContext.repo);
        assert.ok(options.githubContext.prNumber);

        await commentPR(options.githubContext.token, options.githubContext.owner, options.githubContext.repo, options.githubContext.prNumber, report);
    }
    return report;
}