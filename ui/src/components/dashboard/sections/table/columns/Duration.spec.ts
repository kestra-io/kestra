import {afterEach, beforeEach, describe, expect, it, vi} from "vitest";
import {mount} from "@vue/test-utils";
import {Utils} from "@kestra-io/ui-libs";

import Duration from "./Duration.vue";

const START = "2026-10-19T10:00:00.000Z";

describe("dashboard table Duration column", () => {
    beforeEach(() => {
        vi.useFakeTimers();
        vi.setSystemTime(new Date(START).getTime() + 10_000);
    });

    afterEach(() => vi.useRealTimers());

    it("should keep counting a running execution when a refresh re-renders it with the same props", async () => {
        const wrapper = mount(Duration, {props: {field: undefined, startDate: START}});
        expect(wrapper.text()).toBe(Utils.humanDuration(10));

        await vi.advanceTimersByTimeAsync(60_000);
        await wrapper.setProps({field: undefined, startDate: START});

        expect(wrapper.text()).toBe(Utils.humanDuration(70));
    });

    it("should show the duration sent by the backend once the execution reaches a terminal state", async () => {
        const wrapper = mount(Duration, {props: {field: undefined, startDate: START}});

        await vi.advanceTimersByTimeAsync(60_000);
        await wrapper.setProps({field: 65, startDate: START});

        await vi.advanceTimersByTimeAsync(5_000);
        expect(wrapper.text()).toBe(Utils.humanDuration(65));
    });
});
