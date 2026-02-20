import {describe, expect, it} from "vitest";
import {isSuccessfulFlowSaveOutcome, type FlowSaveOutcome} from "../../../src/stores/flow";

describe("flow save outcome", () => {
    it("returns true only for successful outcomes", () => {
        const successful: FlowSaveOutcome[] = ["saved", "redirect_to_update"];
        const unsuccessful: FlowSaveOutcome[] = ["blocked", "no_op", "confirmOutdatedSaveDialog"];

        successful.forEach((outcome) => {
            expect(isSuccessfulFlowSaveOutcome(outcome)).toBe(true);
        });

        unsuccessful.forEach((outcome) => {
            expect(isSuccessfulFlowSaveOutcome(outcome)).toBe(false);
        });
    });
});
