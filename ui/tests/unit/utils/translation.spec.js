import {describe, it, expect} from "vitest";
import fs from "fs";
import path from "path";

const readJsonFile = (filePath) => {
    const fileContent = fs.readFileSync(filePath, "utf-8");
    return JSON.parse(fileContent);
};

const extractKeys = (obj) => {
    let result = [];
    for (var k in obj) {
        if (typeof obj[k] === "object") {
            result = result.concat(extractKeys(obj[k]));
        }
        result = result.concat([k]);
    }
    return result.filter(key => key !== "fr" || key !== "en");
};

describe("Translation Keys", () => {
    it("should have all keys in all languages files", () => {
        const frFilePath = path.resolve(__dirname, "../../../src/translations/fr.json");
        const enFilePath = path.resolve(__dirname, "../../../src/translations/en.json");
        const cnFilePath = path.resolve(__dirname, "../../../src/translations/zh_CN.json");

        const frJson = readJsonFile(frFilePath);
        const enJson = readJsonFile(enFilePath);
        const cnJson = readJsonFile(cnFilePath);

        const frKeys = extractKeys(frJson).filter(key => key !== "fr");
        const enKeys = extractKeys(enJson).filter(key => key !== "en");
        const cnKeys = extractKeys(cnJson).filter(key => key !== "zh_CN");

        const missingInEn = frKeys.filter(key => !enKeys.includes(key));
        const missingInFr = enKeys.filter(key => !frKeys.includes(key));
        const missingInCn = enKeys.filter(key => !cnKeys.includes(key));

        expect(missingInEn).toEqual([]);
        expect(missingInFr).toEqual([]);
        expect(missingInCn).toEqual([]);
    });
});