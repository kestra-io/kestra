import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import ProposedActionCard from "../../../../../src/components/ai/copilot/ProposedActionCard.vue"
import {mountGlobal} from "./_helpers"
import type {ProposedActionEvent} from "../../../../../src/components/ai/copilot/types"

const planAction: ProposedActionEvent = {
    confirmationId: "c1",
    tool: null,
    title: "Add test coverage",
    summary: "Plan the tests",
    steps: [
        {title: "Mock external task outputs", detail: "tests/ai-summarize.test.yml"},
        {title: "Assert the Slack notification fires", detail: "tests/ai-summarize.test.yml"},
    ],
}
const mutateAction: ProposedActionEvent = {
    confirmationId: "c2", tool: "restart-execution", family: "MUTATE", summary: "Restart exec-1", arguments: {id: "exec-1"},
}

const mountCard = (action: ProposedActionEvent, props = {}) =>
    mount(ProposedActionCard, {props: {action, ...props}, global: mountGlobal})

const approve = (w: ReturnType<typeof mountCard>) => w.find("[data-test=\"copilot-approve\"]")
const reject = (w: ReturnType<typeof mountCard>) => w.find("[data-test=\"copilot-reject\"]")

describe("ProposedActionCard", () => {
    it("renders a Plan card: title, pending status, numbered steps, revise + execute footer", () => {
        const w = mountCard(planAction)
        expect(w.text()).toContain("Add test coverage")
        expect(w.text()).toContain("Pending approval")
        const steps = w.findAll(".proposed-step")
        expect(steps).toHaveLength(2)
        expect(steps[0].text()).toContain("Mock external task outputs")
        expect(steps[0].text()).toContain("tests/ai-summarize.test.yml")
        expect(reject(w).text()).toBe("Reply to revise")
        expect(approve(w).text()).toBe("Approve & execute")
        expect(w.find(".ks-tag").exists()).toBe(false) // no family tag on a plan
    })

    it("renders an action card: generic title, family tag, Reject + Approve, and the summary", () => {
        const w = mountCard(mutateAction)
        expect(w.text()).toContain("Proposed action")
        expect(w.find(".ks-tag").text()).toBe("MUTATE")
        expect(w.text()).toContain("Restart exec-1")
        expect(w.findAll(".proposed-step")).toHaveLength(0) // no steps → summary text
        expect(reject(w).text()).toBe("Reject")
        expect(approve(w).text()).toBe("Approve")
    })

    it("shows the tool's identifying arguments", () => {
        const args = mountCard(mutateAction).find("[data-test=\"copilot-proposed-args\"]")
        expect(args.exists()).toBe(true)
        expect(args.text()).toContain("id")
        expect(args.text()).toContain("exec-1")
    })

    it("omits verbose (long) and non-scalar arguments from the args list", () => {
        const w = mountCard({
            confirmationId: "c8", tool: "create-flow", family: "MUTATE", summary: "Create flow",
            arguments: {namespace: "company.team", flowId: "my-flow", body: "id: my-flow\n".repeat(50), labels: ["a"]},
        })
        const args = w.find("[data-test=\"copilot-proposed-args\"]")
        expect(args.text()).toContain("company.team")
        expect(args.text()).toContain("my-flow")
        expect(args.text()).not.toContain("labels") // array omitted
        expect(args.text()).not.toContain("id: my-flow") // long YAML body omitted
    })

    it("shows no args block for a plan card", () => {
        expect(mountCard(planAction).find("[data-test=\"copilot-proposed-args\"]").exists()).toBe(false)
    })

    it("falls back to a generic plan title when none is provided", () => {
        const w = mountCard({confirmationId: "c3", tool: null, summary: "do things"})
        expect(w.text()).toContain("Proposed plan")
    })

    it("emits approve / reject (no reason) on the footer buttons", async () => {
        const w = mountCard(mutateAction)
        await approve(w).trigger("click")
        expect(w.emitted("approve")).toHaveLength(1)
        await reject(w).trigger("click")
        expect(w.emitted("reject")).toHaveLength(1)
    })

    it("disables both actions while a decision is in flight", () => {
        const w = mountCard(mutateAction, {disabled: true})
        expect(approve(w).attributes("disabled")).toBeDefined()
        expect(reject(w).attributes("disabled")).toBeDefined()
    })
})
