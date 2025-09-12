import core from '@actions/core';
import {context} from '@actions/github';
const GITHUB_TOKEN = core.getInput('GITHUB_TOKEN');
import {strict as assert} from 'assert';

export function getPRContext():{token: string, owner: string, repo: string, prNumber: number}{
    assert.ok(GITHUB_TOKEN, "GITHUB_TOKEN is mandatory");
    assert.ok(context.issue);
    assert.ok(context.issue.owner);
    assert.ok(context.issue.repo);
    assert.ok(context.issue.number);

    return {token: GITHUB_TOKEN, owner: context.repo.owner, repo: context.repo.repo, prNumber: context.issue.number }
}