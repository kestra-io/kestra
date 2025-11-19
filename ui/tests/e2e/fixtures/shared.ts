/**
 * TODO: use ENV instead
 */
export const shared = {
    namespace: "company.team",
    username: process.env.E2E_USERNAME ?? "user@kestra.io",
    password: process.env.E2E_PASSWORD ?? "DemoDemo1"
};