const fs = require("fs")

const COMMENT_MARKER = "<!-- kestra:ui-test-failures-report -->"

function decodeXmlEntities(value) {
    return value
        .replace(/&quot;/g, "\"")
        .replace(/&apos;/g, "'")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&amp;/g, "&")
}

function extractAttribute(tag, attribute) {
    // `\b` (not just a plain match) matters here: "classname" contains "name"
    // as a substring, and a bare `name="..."` pattern would match inside it.
    const match = tag.match(new RegExp(`\\b${attribute}="([^"]*)"`))
    return match ? decodeXmlEntities(match[1]) : undefined
}

// Vitest's built-in "junit" reporter output is flat enough (testsuite >
// testcase > failure|error, all attributes on the opening tag) for a small
// regex parser — pulling in a full XML library for this would be overkill.
function parseJUnitFailures(xml, category) {
    const failures = []
    const testsuiteBlocks = xml.match(/<testsuite\b[^>]*>[\s\S]*?<\/testsuite>/g) ?? []

    for (const suiteBlock of testsuiteBlocks) {
        const file = extractAttribute(suiteBlock.match(/<testsuite\b[^>]*>/)[0], "name")
        const testcaseBlocks = suiteBlock.match(/<testcase\b[^>]*?(?:\/>|>[\s\S]*?<\/testcase>)/g) ?? []

        for (const testcaseBlock of testcaseBlocks) {
            const failureBlock = testcaseBlock.match(/<(failure|error)\b[^>]*?(?:\/>|>[\s\S]*?<\/\1>)/)
            if (!failureBlock) continue

            const testcaseOpenTag = testcaseBlock.match(/<testcase\b[^>]*>/)?.[0] ?? testcaseBlock
            failures.push({
                category,
                file,
                name: extractAttribute(testcaseOpenTag, "name"),
                message: extractAttribute(failureBlock[0], "message") ?? "See the CI logs for details.",
            })
        }
    }

    return failures
}

// Best-effort: point the link at the exact `it(`/`test(` line when we can
// find it in the checked-out source, otherwise fall back to the file itself.
function findLineNumber(repoRelativePath, testName) {
    if (!fs.existsSync(repoRelativePath)) return undefined

    const lastSegment = testName?.split(" > ").pop()
    if (!lastSegment) return undefined

    const lines = fs.readFileSync(repoRelativePath, "utf8").split("\n")
    const index = lines.findIndex((line) => line.includes(lastSegment))
    return index === -1 ? undefined : index + 1
}

function buildCommentBody({failures, repository, sha, basePath, repoPath, runUrl, maxListedFailures}) {
    if (failures.length === 0) return null

    const listed = failures.slice(0, maxListedFailures)
    const lines = [
        COMMENT_MARKER,
        `### :x: ${failures.length} UI test${failures.length > 1 ? "s" : ""} failed`,
    ]

    if (failures.length > maxListedFailures) {
        lines.push(
            "",
            `Showing the first ${maxListedFailures} below — see the [full run](${runUrl}) for all ${failures.length} failures.`,
        )
    }

    for (const failure of listed) {
        // These two differ when the job checks out this repo into a
        // subdirectory alongside another one (e.g. the EE frontend job
        // checks kestra-ee out under "kestra-ee/"): `basePath` is where to
        // find the file on disk, `repoPath` is where it lives in the repo
        // itself, which is what a GitHub blob URL must be relative to.
        const diskPath = `${basePath}/${failure.file}`
        const repoRelativePath = `${repoPath}/${failure.file}`
        const line = findLineNumber(diskPath, failure.name)
        const url = `https://github.com/${repository}/blob/${sha}/${repoRelativePath}${line ? `#L${line}` : ""}`

        lines.push(
            "",
            `#### \`${failure.name}\``,
            `- **Category:** ${failure.category}`,
            `- **Test:** [${repoRelativePath}](${url})`,
            "- **Error:**",
            "  ```",
            `  ${failure.message}`,
            "  ```",
        )
    }

    return lines.join("\n")
}

async function findExistingCommentId({github, context}) {
    const comments = await github.paginate(github.rest.issues.listComments, {
        owner: context.repo.owner,
        repo: context.repo.repo,
        issue_number: context.issue.number,
    })
    return comments.find((comment) => comment.body?.includes(COMMENT_MARKER))?.id
}

async function upsertComment({github, context, core, body}) {
    const existingId = await findExistingCommentId({github, context})

    if (!body) {
        if (existingId) {
            await github.rest.issues.deleteComment({
                owner: context.repo.owner,
                repo: context.repo.repo,
                comment_id: existingId,
            })
            core.info("No UI test failures — removed the stale failures comment.")
        } else {
            core.info("No UI test failures — nothing to report.")
        }
        return
    }

    if (existingId) {
        await github.rest.issues.updateComment({
            owner: context.repo.owner,
            repo: context.repo.repo,
            comment_id: existingId,
            body,
        })
    } else {
        await github.rest.issues.createComment({
            owner: context.repo.owner,
            repo: context.repo.repo,
            issue_number: context.issue.number,
            body,
        })
    }
}

async function run({github, context, core, basePath, repoPath, reports, maxListedFailures}) {
    if (!context.payload.pull_request) {
        core.info("Not running on a pull request — skipping UI test failure report.")
        return
    }

    const failures = reports.flatMap(({path: reportPath, category}) => {
        if (!reportPath || !fs.existsSync(reportPath)) return []
        return parseJUnitFailures(fs.readFileSync(reportPath, "utf8"), category)
    })

    const repository = `${context.repo.owner}/${context.repo.repo}`
    const sha = context.payload.pull_request.head.sha
    const runUrl = `${context.serverUrl}/${repository}/actions/runs/${context.runId}`

    const body = buildCommentBody({failures, repository, sha, basePath, repoPath: repoPath || basePath, runUrl, maxListedFailures})
    await upsertComment({github, context, core, body})
}

module.exports = {run}
