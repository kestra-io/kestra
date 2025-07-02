import {describe, it, expect} from "vitest";
import translationEn from "../../../src/translations/en.json";

function flattenKeys(obj, prefix = "") {
    return Object.keys(obj).reduce((acc, key) => {
        const newKey = prefix ? `${prefix}.${key}` : key;
        if (typeof obj[key] === "object" && obj[key] !== null) {
            acc.push(...flattenKeys(obj[key], newKey));
        } else {
            acc.push(newKey);
        }
        return acc;
    }, []);
}

const enKeys = flattenKeys(translationEn.en)

const translationFiles = import.meta.glob("../../../src/translations/*.json", {
    eager: true,
    import: "default",
});

describe("Translation Keys", () => {
    for(const key in translationFiles){
        const language = key.split("/").pop().replace(/\.json$/, "");
        if (language === "en") {
            continue; // Skip the English file as it is the reference
        }
        it(`should have all keys in "${language}.json" language files`, () => {
            const fileKeys = flattenKeys(translationFiles[key][language]);
            expect(fileKeys).toBeDefined();
            expect(fileKeys.length).toBeGreaterThan(0);
            expect(enKeys.filter(k => !fileKeys.includes(k))).toEqual([]);
        });
    }
});