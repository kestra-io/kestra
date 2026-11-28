import {describe, it, expect, vi, beforeEach} from "vitest"
import type {RouteParams} from "vue-router"
import resource from "../../../models/resource"
import action from "../../../models/action"

const push = vi.fn()
let routeParams: RouteParams = {}
vi.mock("vue-router", () => ({
    useRouter: () => ({push}),
    useRoute: () => ({params: routeParams}),
}))

let edition = "OSS"
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {edition}}),
}))

const hasAnyAction = vi.fn()
let user: {hasAnyAction?: typeof hasAnyAction} | undefined = {hasAnyAction}
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user}),
}))

import {useToolFlowCreation} from "./useToolFlowCreation"

describe("useToolFlowCreation", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        routeParams = {}
        edition = "OSS"
        user = {hasAnyAction}
        hasAnyAction.mockReturnValue(false)
    })

    it("canCreateFlow is true in OSS regardless of the user's actions", () => {
        edition = "OSS"
        hasAnyAction.mockReturnValue(false)
        const {canCreateFlow} = useToolFlowCreation()
        expect(canCreateFlow.value).toBe(true)

        user = undefined
        const {canCreateFlow: canCreateWithoutUser} = useToolFlowCreation()
        expect(canCreateWithoutUser.value).toBe(true)
    })

    it("outside OSS it follows hasAnyAction(FLOW, CREATE), and is false when there is no user", () => {
        edition = "EE"
        hasAnyAction.mockReturnValue(true)
        const {canCreateFlow: canCreateWhenAllowed} = useToolFlowCreation()
        expect(canCreateWhenAllowed.value).toBe(true)
        expect(hasAnyAction).toHaveBeenCalledWith(resource.FLOW, action.CREATE)

        hasAnyAction.mockReturnValue(false)
        const {canCreateFlow: canCreateWhenForbidden} = useToolFlowCreation()
        expect(canCreateWhenForbidden.value).toBe(false)

        user = undefined
        const {canCreateFlow: canCreateWithoutUser} = useToolFlowCreation()
        expect(canCreateWithoutUser.value).toBe(false)
    })

    it("createToolFlow pushes flows/create with the mcp-tool-trigger blueprint id", () => {
        const {createToolFlow} = useToolFlowCreation()
        createToolFlow()

        expect(push).toHaveBeenCalledWith(
            expect.objectContaining({
                name: "flows/create",
                query: expect.objectContaining({
                    blueprintId: "mcp-tool-trigger",
                }),
            }),
        )
    })

    it("the generated YAML carries the route's server id as mcpServer", () => {
        routeParams = {id: "custom-mcp-server"}
        const {createToolFlow} = useToolFlowCreation()
        createToolFlow()

        expect(push).toHaveBeenCalledWith(
            expect.objectContaining({
                query: expect.objectContaining({
                    blueprintSourceYaml: expect.stringContaining("mcpServer: custom-mcp-server"),
                }),
            }),
        )
    })

    it("a missing route id falls back to default", () => {
        routeParams = {}
        const {createToolFlow} = useToolFlowCreation()
        createToolFlow()

        expect(push).toHaveBeenCalledWith(
            expect.objectContaining({
                query: expect.objectContaining({
                    blueprintSourceYaml: expect.stringContaining("mcpServer: default"),
                }),
            }),
        )
    })

    it("the tenant param is forwarded when present and omitted when absent", () => {
        routeParams = {tenant: "my-tenant"}
        const {createToolFlow: createWithTenant} = useToolFlowCreation()
        createWithTenant()

        expect(push).toHaveBeenCalledWith(
            expect.objectContaining({
                params: {tenant: "my-tenant"},
            }),
        )

        push.mockClear()
        routeParams = {}
        const {createToolFlow: createWithoutTenant} = useToolFlowCreation()
        createWithoutTenant()

        expect(push).toHaveBeenCalledWith(
            expect.not.objectContaining({
                params: expect.anything(),
            }),
        )
    })
})
