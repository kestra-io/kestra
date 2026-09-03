/**
 * TODO: use ENV instead
 */
export const shared = {
    get namespace() { return process.env.E2E_NAMESPACE ?? "company.team" },
    get username() { return process.env.E2E_USERNAME ?? "user@kestra.io" },
    get password() { return process.env.E2E_PASSWORD ?? "DemoDemo1" },
}