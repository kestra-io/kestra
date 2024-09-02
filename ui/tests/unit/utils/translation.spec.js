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
        const deFilePath = path.resolve(__dirname, "../../../src/translations/de.json");
        const enFilePath = path.resolve(__dirname, "../../../src/translations/en.json");
        const esFilePath = path.resolve(__dirname, "../../../src/translations/es.json");
        const frFilePath = path.resolve(__dirname, "../../../src/translations/fr.json");
        const hiFilePath = path.resolve(__dirname, "../../../src/translations/hi.json");
        const itFilePath = path.resolve(__dirname, "../../../src/translations/it.json");
        const jaFilePath = path.resolve(__dirname, "../../../src/translations/ja.json");
        const koFilePath = path.resolve(__dirname, "../../../src/translations/ko.json");
        const plFilePath = path.resolve(__dirname, "../../../src/translations/pl.json");
        const ptFilePath = path.resolve(__dirname, "../../../src/translations/pt.json");
        const ruFilePath = path.resolve(__dirname, "../../../src/translations/ru.json");
        const cnFilePath = path.resolve(__dirname, "../../../src/translations/zh_CN.json");

        const deJson = readJsonFile(deFilePath);
        const enJson = readJsonFile(enFilePath);
        const esJson = readJsonFile(esFilePath);
        const frJson = readJsonFile(frFilePath);
        const hiJson = readJsonFile(hiFilePath);
        const itJson = readJsonFile(itFilePath);
        const jaJson = readJsonFile(jaFilePath);
        const koJson = readJsonFile(koFilePath);
        const plJson = readJsonFile(plFilePath);
        const ptJson = readJsonFile(ptFilePath);
        const ruJson = readJsonFile(ruFilePath);
        const cnJson = readJsonFile(cnFilePath);

        const deKeys = extractKeys(deJson).filter(key => key !== "de");
        const enKeys = extractKeys(enJson).filter(key => key !== "en");
        const esKeys = extractKeys(esJson).filter(key => key !== "es");
        const frKeys = extractKeys(frJson).filter(key => key !== "fr");
        const hiKeys = extractKeys(hiJson).filter(key => key !== "hi");
        const itKeys = extractKeys(itJson).filter(key => key !== "it");
        const jaKeys = extractKeys(jaJson).filter(key => key !== "ja");
        const koKeys = extractKeys(koJson).filter(key => key !== "ko");
        const plKeys = extractKeys(plJson).filter(key => key !== "pl");
        const ptKeys = extractKeys(ptJson).filter(key => key !== "pt");
        const ruKeys = extractKeys(ruJson).filter(key => key !== "ru");
        const cnKeys = extractKeys(cnJson).filter(key => key !== "zh_CN");

        const missingInDe = enKeys.filter(key => !deKeys.includes(key));
        const missingInEn = frKeys.filter(key => !enKeys.includes(key));
        const missingInEs = enKeys.filter(key => !esKeys.includes(key));
        const missingInFr = enKeys.filter(key => !frKeys.includes(key));
        const missingInHi = enKeys.filter(key => !hiKeys.includes(key));
        const missingInIt = enKeys.filter(key => !itKeys.includes(key));
        const missingInJa = enKeys.filter(key => !jaKeys.includes(key));
        const missingInKo = enKeys.filter(key => !koKeys.includes(key));
        const missingInPl = enKeys.filter(key => !plKeys.includes(key));
        const missingInPt = enKeys.filter(key => !ptKeys.includes(key));
        const missingInRu = enKeys.filter(key => !ruKeys.includes(key));
        const missingInCn = enKeys.filter(key => !cnKeys.includes(key));

        expect(missingInDe).toEqual([]);
        expect(missingInEn).toEqual([]);
        expect(missingInEs).toEqual([]);
        expect(missingInFr).toEqual([]);
        expect(missingInHi).toEqual([]);
        expect(missingInIt).toEqual([]);
        expect(missingInJa).toEqual([]);
        expect(missingInKo).toEqual([]);
        expect(missingInPl).toEqual([]);
        expect(missingInPt).toEqual([]);
        expect(missingInRu).toEqual([]);
        expect(missingInCn).toEqual([]);
    });
});