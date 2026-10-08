import {describe, expect, it, vi} from "vitest"
import {canSaveFlowTemplate} from "../../../src/utils/flowTemplate"
import action from "../../../src/models/action"
import resource from "../../../src/models/resource"

describe("canSaveFlowTemplate", () => {
    const item = {namespace: "io.kestra.tests"}
    const user = {
        isAllowed: vi.fn(),
    }

    it("allows saving when the item is undefined", () => {
        expect(canSaveFlowTemplate(false, user, undefined, "flow")).toBe(true)
        expect(user.isAllowed).not.toHaveBeenCalled()
    })

    it("checks UPDATE permission in edit mode", () => {
        user.isAllowed.mockReturnValue(true)

        expect(canSaveFlowTemplate(true, user, item, "flow")).toBe(true)
        expect(user.isAllowed).toHaveBeenCalledWith(
            resource.FLOW,
            action.UPDATE,
            item.namespace,
        )
    })

    it("checks CREATE permission in create mode", () => {
        user.isAllowed.mockReturnValue(true)

        expect(canSaveFlowTemplate(false, user, item, "flow")).toBe(true)
        expect(user.isAllowed).toHaveBeenCalledWith(
            resource.FLOW,
            action.CREATE,
            item.namespace,
        )
    })

    it("returns falsy when there is no user", () => {
        expect(canSaveFlowTemplate(true, undefined, item, "flow")).toBeFalsy()
        expect(canSaveFlowTemplate(false, undefined, item, "flow")).toBeFalsy()
    })

    it("returns falsy when the user is not allowed", () => {
        user.isAllowed.mockReturnValue(false)

        expect(canSaveFlowTemplate(true, user, item, "flow")).toBeFalsy()
        expect(canSaveFlowTemplate(false, user, item, "flow")).toBeFalsy()
    })

    it("uppercases the data type before looking up the resource", () => {
        user.isAllowed.mockReturnValue(true)

        expect(canSaveFlowTemplate(true, user, item, "fLoW")).toBe(true)
        expect(user.isAllowed).toHaveBeenCalledWith(
            resource.FLOW,
            action.UPDATE,
            item.namespace,
        )
    })
})