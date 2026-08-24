import {afterEach, describe, expect, it} from "vitest";
import {humanizeNumber} from "../../../src/utils/filters";

describe("humanizeNumber", () => {
    afterEach(() => {
        localStorage.removeItem("lang");
    });

    it("formats with the default language when none is stored", () => {
        expect(humanizeNumber("1234567")).toBe((1234567).toLocaleString("en"));
    });

    // Underscore codes are not valid BCP 47 tags: passed raw to toLocaleString they
    // throw RangeError, which is what happened for the pt_BR and zh_CN locales.
    it.each(["pt_BR", "zh_CN"])("formats for the underscore locale %s instead of throwing", (lang) => {
        localStorage.setItem("lang", lang);

        expect(humanizeNumber("1234567")).toBe((1234567).toLocaleString(lang.replace("_", "-")));
    });

    it("formats for a plain locale code", () => {
        localStorage.setItem("lang", "de");

        expect(humanizeNumber("1234567")).toBe((1234567).toLocaleString("de"));
    });
});
