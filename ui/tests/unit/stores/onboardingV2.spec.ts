import {beforeEach, describe, expect, it} from "vitest";
import {createPinia, setActivePinia} from "pinia";

describe("onboardingV2 store", () => {
    beforeEach(() => {
        localStorage.clear();
        setActivePinia(createPinia());
    });

    it("starts guided mode at first flow step", async () => {
        const {useOnboardingV2Store} = await import("../../../src/stores/onboardingV2");
        const store = useOnboardingV2Store();

        store.startGuided();

        expect(store.state.status).toBe("in_progress");
        expect(store.state.mode).toBe("guided");
        expect(store.state.guideId).toBe("first_flow");
        expect(store.state.currentStepId).toBe("flow_basics");
        expect(store.state.editorMode).toBe("code_only");
    });

    it("increments save count only while guided tour is active", async () => {
        const {useOnboardingV2Store} = await import("../../../src/stores/onboardingV2");
        const store = useOnboardingV2Store();

        store.recordSave();
        expect(store.state.saveCount).toBe(0);

        store.startGuided();
        store.recordSave();
        expect(store.state.saveCount).toBe(1);

        store.complete();
        store.recordSave();
        expect(store.state.saveCount).toBe(1);
    });
});
