import fs from "fs";
import path from "path";
import {fileURLToPath} from "url";

const getPath = (lang) => path.resolve(path.dirname(fileURLToPath(import.meta.url)), `./${lang}.json`);
const readJSON = (filePath) => JSON.parse(fs.readFileSync(filePath, "utf-8"));

const getNestedKeys = (obj, prefix = "") =>
    Object.keys(obj).reduce((keys, key) => {
        const fullKey = prefix ? `${prefix}.${key}` : key;
        keys.push(fullKey);
        if (
            typeof obj[key] === "object" &&
            obj[key] &&
            !Array.isArray(obj[key])
        ) {
            keys.push(...getNestedKeys(obj[key], fullKey));
        }
        return keys;
    }, []);

// Use English as a base language
const content = getNestedKeys(readJSON(getPath("en"))["en"]);

const languages = ["de", "es", "fr", "hi", "it", "ja", "ko", "pl", "pt", "ru", "zh_CN"];
const paths = languages.map((lang) => getPath(lang));

languages.forEach((lang, i) => {
    const current = getNestedKeys(readJSON(paths[i])[lang]);

    const missing = content.filter((key) => !current.includes(key));
    const extra = current.filter((key) => !content.includes(key));

    console.log(`---\n\x1b[34mComparison with ${lang.toUpperCase()}\x1b[0m  \n`);
    console.log(missing.length ? `Missing keys: \x1b[31m${missing.join(", ")}\x1b[0m` : "No missing keys.");
    console.log(extra.length ? `Extra keys: \x1b[32m${extra.join(", ")}\x1b[0m` : "No extra keys.");
    console.log("---\n");
});
