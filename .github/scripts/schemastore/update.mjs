import {readFileSync, writeFileSync, mkdirSync} from "node:fs";
import {createRequire} from "node:module";
import {resolve, dirname} from "node:path";
import {pathToFileURL} from "node:url";

export function releaseVersion(tag) {
    if (!/^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/.test(tag)) {
        throw new Error(`Expected a stable release tag vMAJOR.MINOR.PATCH, received: ${tag}`);
    }
    return tag.slice(1);
}

function compareVersions(left, right) {
    const a = left.split(".").map(Number);
    const b = right.split(".").map(Number);
    return a[0] - b[0] || a[1] - b[1] || a[2] - b[2];
}

export function flowSchema(bundle, version) {
    const root = bundle.roots?.flow;
    if (!root?.startsWith("#/definitions/") || !bundle.definitions || !bundle.plugins?.length) {
        throw new Error("The release must contain a full plugin schema bundle with a flow root.");
    }

    const definitions = {};
    function visit(value) {
        if (!value || typeof value !== "object") return;
        if (typeof value.$ref === "string") {
            if (!value.$ref.startsWith("#/definitions/")) {
                throw new Error(`Unsupported schema reference: ${value.$ref}`);
            }
            const name = value.$ref.slice("#/definitions/".length).split("/")[0]
                .replaceAll("~1", "/").replaceAll("~0", "~");
            if (!Object.hasOwn(bundle.definitions, name)) {
                throw new Error(`Missing schema definition: ${name}`);
            }
            if (!Object.hasOwn(definitions, name)) {
                definitions[name] = bundle.definitions[name];
                visit(definitions[name]);
            }
        }
        for (const child of Object.values(value)) visit(child);
    }
    visit({$ref: root});

    return {
        $schema: "http://json-schema.org/draft-07/schema#",
        $id: `https://www.schemastore.org/kestra-${version}.json`,
        $ref: root,
        definitions: Object.fromEntries(Object.entries(definitions).sort(([a], [b]) => a.localeCompare(b))),
    };
}

export function updateSchemaStore(directory, bundle, tag) {
    const version = releaseVersion(tag);
    const schema = flowSchema(bundle, version);
    const require = createRequire(resolve(process.env.SCHEMASTORE_DIR ?? directory, "package.json"));
    const {parse, modify, applyEdits} = require("jsonc-parser");
    const catalogPath = resolve(directory, "src/api/json/catalog.json");
    const validationPath = resolve(directory, "src/schema-validation.jsonc");
    let catalogText = readFileSync(catalogPath, "utf8");
    let validationText = readFileSync(validationPath, "utf8");
    const catalog = JSON.parse(catalogText);
    const errors = [];
    const validation = parse(validationText, errors);
    if (errors.length) throw new Error("SchemaStore validation configuration is invalid JSONC.");
    const index = catalog.schemas.findIndex(entry => entry.name === "Kestra flow file");
    if (index < 0) throw new Error("The Kestra catalog entry is missing.");
    const entry = catalog.schemas[index];
    const versions = Object.keys(entry.versions);
    for (const previous of versions) releaseVersion(`v${previous}`);
    const latest = versions.sort(compareVersions).at(-1);
    const filename = `kestra-${version}.json`;
    const template = `kestra-${latest}.json`;
    if (!validation.ajvNotStrictMode?.includes(template) || !validation.options?.[template]) {
        throw new Error("The existing Kestra validation configuration needs review.");
    }
    const edit = (text, path, value, options = {}) => applyEdits(text, modify(text, path, value, {
        formattingOptions: {insertSpaces: true, tabSize: 2, eol: "\n"},
        ...options,
    }));
    const updatedVersions = {...entry.versions, [version]: schema.$id};
    catalogText = edit(catalogText, ["schemas", index, "versions"],
        Object.fromEntries(Object.entries(updatedVersions).sort(([a], [b]) => compareVersions(a, b))));
    if (compareVersions(version, latest) >= 0) {
        catalogText = edit(catalogText, ["schemas", index, "url"], schema.$id);
    }
    if (!validation.ajvNotStrictMode.includes(filename)) {
        const position = validation.ajvNotStrictMode.indexOf(template) + 1;
        validationText = edit(validationText, ["ajvNotStrictMode", position], filename, {isArrayInsertion: true});
    }
    const settings = validation.options[filename] ?? validation.options[template];
    validationText = edit(validationText, ["options", filename], {
        ...settings,
        unknownFormat: [...new Set([...(settings.unknownFormat ?? []), "partial-time"])].sort(),
        unknownKeywords: [...new Set([
            ...(settings.unknownKeywords ?? []),
            "$beta", "$deprecated", "$dynamic", "$group", "$index",
            "$internalStorageURI", "$language", "$metrics", "$secret",
        ])].sort(),
    }, {getInsertionIndex: properties => properties.indexOf(template) + 1});

    const schemaPath = resolve(directory, "src/schemas/json", filename);
    const testPath = resolve(directory, "src/test", `kestra-${version}`, "hello-world.yaml");
    mkdirSync(dirname(schemaPath), {recursive: true});
    mkdirSync(dirname(testPath), {recursive: true});
    writeFileSync(schemaPath, JSON.stringify(schema, null, 2) + "\n");
    writeFileSync(testPath, `# yaml-language-server: $schema=../../schemas/json/${filename}\nid: hello-world\nnamespace: company.team\ntasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log\n    message: Hello World\n`);
    writeFileSync(catalogPath, catalogText);
    writeFileSync(validationPath, validationText);
    return filename;
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
    const [directory, bundlePath, tag] = process.argv.slice(2);
    if (!directory || !bundlePath || !tag) {
        throw new Error("Usage: node update.mjs SCHEMASTORE_DIRECTORY PLUGIN_BUNDLE RELEASE_TAG");
    }
    console.log(updateSchemaStore(directory, JSON.parse(readFileSync(bundlePath, "utf8")), tag));
}
