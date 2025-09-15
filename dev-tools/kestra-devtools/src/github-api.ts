import { Octokit} from "octokit";

export async function commentPR(githubToken: string, owner: string, repo: string, prNumber: number, content: string){
    const octokit = new Octokit({ auth: githubToken });

    await octokit.rest.issues.createComment({
        owner,
        repo,
        issue_number:prNumber,
        body: content,
    });
}

