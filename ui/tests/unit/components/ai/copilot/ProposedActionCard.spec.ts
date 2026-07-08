import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import ProposedActionCard from "../../../../../src/components/ai/copilot/ProposedActionCard.vue"
import {mountGlobal} from "./_helpers"
import type {ProposedActionEvent} from "../../../../../src/components/ai/copilot/types"

const planAction: ProposedActionEvent = {confirmationId: "c1", tool: null, summary: "Read logs then restart"}
const mutateAction: ProposedActionEvent = {
    confirmationId: "c2", tool: "restart-execution", family: "MUTATE", summary: "Restart exec-1", arguments: {id: "exec-1"},
}

const mountCard = (action: ProposedActionEvent, props = {}) =>
    mount(ProposedActionCard, {props: {action, ...props}, global: mountGlobal})

const approve = (w: ReturnType<typeof mountCard>) => w.find("[data-test=\"copilot-approve\"]")
const reject = (w: ReturnType<typeof mountCard>) => w.find("[data-test=\"copilot-reject\"]")
const reason = (w: ReturnType<typeof mountCard>) => w.find("[data-test=\"copilot-confirm-reason\"]")

describe("ProposedActionCard", () => {
    it("renders a Plan card (null tool) with the plan title and Approve & execute", () => {
        const w = mountCard(planAction)
        expect(w.text()).toContain("Proposed plan")
        expect(approve(w).text()).toBe("Approve & execute")
        expect(w.find(".ks-tag").exists()).toBe(false) // no family for a plan
    })

    it("renders an action card (concrete tool) with the action title, Approve, and family tag", () => {
        const w = mountCard(mutateAction)
        expect(w.text()).toContain("Proposed action")
        expect(approve(w).text()).toBe("Approve")
        expect(w.find(".ks-tag").text()).toBe("MUTATE")
        expect(w.text()).toContain("Restart exec-1")
    })

    it("emits approve with an undefined reason when the box is empty", async () => {
        const w = mountCard(mutateAction)
        await approve(w).trigger("click")
        expect(w.emitted("approve")?.[0]).toEqual([undefined])
    })

    it("emits approve/reject with the trimmed steering reason", async () => {
        const w = mountCard(mutateAction)
        await reason(w).setValue("  use the staging env  ")
        await reject(w).trigger("click")
        expect(w.emitted("reject")?.[0]).toEqual(["use the staging env"])
    })

    it("disables both buttons while a decision is in flight", () => {
        const w = mountCard(mutateAction, {disabled: true})
        expect(approve(w).attributes("disabled")).toBeDefined()
        expect(reject(w).attributes("disabled")).toBeDefined()
    })
})
