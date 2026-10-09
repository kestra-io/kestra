import {describe, it, expect} from "vitest"

import routes from "./routes"

const DEMO_PAGES = [
    "dashboards/create",
    "dashboards/update",
    "apps/list",
    "tests/list",
    "assets/list",
    "cases/list",
    "admin/iam",
    "admin/tenants/list",
    "admin/auditlogs/list",
    "admin/quotas/list",
    "admin/policies",
    "admin/instance",
    "promote/targets",
]

describe("routes ossOnly marker", () => {
    it("flags the basic-auth setup wizard as ossOnly", () => {
        // Given
        const setup = routes.find(route => route.name === "setup")

        // Then
        // EE filters on this flag; without it the wizard ships to an edition that cannot serve it.
        expect(setup).toBeDefined()
        expect(setup?.ossOnly).toBe(true)
    })

    it("flags every Enterprise placeholder page as ossOnly, so EE never serves its own upsell", () => {
        const demoRoutes = routes.filter(route => String(route.component).includes("components/demo/"))

        expect(demoRoutes.map(route => route.name)).toEqual(DEMO_PAGES)
        expect(demoRoutes.filter(route => !route.ossOnly).map(route => route.name)).toEqual([])
    })

    it("keeps every other route registrable by downstream editions", () => {
        // Given
        const ossOnlyNames = routes.filter(route => route.ossOnly).map(route => route.name)

        // Then
        // Flagging a route removes it from EE, so any addition should surface in this diff.
        expect(ossOnlyNames).toEqual(["setup", ...DEMO_PAGES])
    })
})
