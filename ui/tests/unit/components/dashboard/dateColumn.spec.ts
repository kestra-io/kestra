import {describe, expect, it, beforeEach, afterEach} from "vitest";
import {mount} from "@vue/test-utils";
import moment from "moment-timezone";

import {DATE_FORMAT_STORAGE_KEY, TIMEZONE_STORAGE_KEY} from "../../../../src/components/settings/BasicSettings.vue";
import DateColumn from "../../../../src/components/dashboard/sections/table/columns/Date.vue";

const FIELD = "2026-07-24T13:16:00.000Z";
const TIMEZONE = "America/Los_Angeles";

describe("dashboard table Date column", () => {
    beforeEach(() => localStorage.clear());
    afterEach(() => localStorage.clear());

    it("should format in the timezone from settings rather than the machine one", () => {
        localStorage.setItem(TIMEZONE_STORAGE_KEY, TIMEZONE);

        const wrapper = mount(DateColumn, {props: {field: FIELD}});

        expect(wrapper.text()).toBe(moment(FIELD).tz(TIMEZONE).format("llll"));
    });

    // The format used to be read once at module scope, so a change in Settings only took
    // effect after a full reload.
    it("should honour the date format from settings alongside the timezone", () => {
        localStorage.setItem(TIMEZONE_STORAGE_KEY, TIMEZONE);
        localStorage.setItem(DATE_FORMAT_STORAGE_KEY, "YYYY-MM-DD HH:mm");

        const wrapper = mount(DateColumn, {props: {field: FIELD}});

        // 13:16 UTC is 06:16 in Los Angeles, so a wrong timezone shows a different hour.
        expect(wrapper.text()).toBe("2026-07-24 06:16");
    });

    it("should render nothing when there is no field", () => {
        expect(mount(DateColumn, {props: {}}).text()).toBe("");
    });
});
