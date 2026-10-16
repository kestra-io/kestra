import {test} from "node:test";
import assert from "node:assert/strict";
import {mkdtempSync, mkdirSync, readFileSync, writeFileSync, rmSync} from "node:fs";
import {tmpdir} from "node:os";
import {resolve} from "node:path";
import {releaseVersion, flowSchema, updateSchemaStore} from "./update.mjs";

const bundle = {
    roots: {flow: "#/definitions/Flow"},
    plugins: [{artifactId: "plugin-core"}],
    definitions: {
        Flow: {type: "object", properties: {task: {$ref: "#/definitions/Task"}}},
        Task: {type: "object", properties: {child: {$ref: "#/definitions/Task"}}},
        Dashboard: {type: "object"},
    },
};

test("rejects prereleases and unsafe release tags", () => {
    assert.equal(releaseVersion("v2.0.5"), "2.0.5");
    for (const tag of ["v2.0.5-rc1", "v2.0.5-SNAPSHOT", "develop", "v02.0.5", "v2.0.5/../x", "2.0.5"]) {
        assert.throws(() => releaseVersion(tag), /stable release tag/);
    }
});

test("retains recursive flow dependencies and excludes unrelated schemas", () => {
    const schema = flowSchema(bundle, "2.0.5");
    assert.deepEqual(Object.keys(schema.definitions), ["Flow", "Task"]);
    assert.equal(schema.$ref, bundle.roots.flow);
    assert.equal(schema.definitions.Task.properties.child.$ref, "#/definitions/Task");
    assert.equal(schema.$id, "https://www.schemastore.org/kestra-2.0.5.json");
});

test("fails on incomplete bundles instead of publishing a partial schema", () => {
    assert.throws(() => flowSchema({...bundle, plugins: []}, "2.0.5"), /full plugin schema bundle/);
    assert.throws(() => flowSchema({...bundle, roots: {}}, "2.0.5"), /full plugin schema bundle/);
    assert.throws(() => flowSchema({...bundle, definitions: {Flow: bundle.definitions.Flow}}, "2.0.5"), /Missing schema definition: Task/);
    assert.throws(() => flowSchema({...bundle, definitions: {Flow: {$ref: "https://example.org/schema.json"}}}, "2.0.5"), /Unsupported schema reference/);
});

function fixture(t) {
    assert.ok(process.env.SCHEMASTORE_DIR, "Set SCHEMASTORE_DIR to a SchemaStore checkout with npm ci completed.");
    const directory = mkdtempSync(resolve(tmpdir(), "kestra-schemastore-test-"));
    t.after(() => rmSync(directory, {recursive: true, force: true}));
    mkdirSync(resolve(directory, "src/api/json"), {recursive: true});
    writeFileSync(resolve(directory, "src/api/json/catalog.json"), JSON.stringify({schemas: [{
        name: "Kestra flow file",
        fileMatch: ["**/flows/*.yml"],
        url: "https://www.schemastore.org/kestra-2.9.0.json",
        versions: {"2.9.0": "https://www.schemastore.org/kestra-2.9.0.json"},
    }, {name: "Other schema", url: "https://example.org/other.json"}]}, null, 2));
    writeFileSync(resolve(directory, "src/schema-validation.jsonc"), `{
  // Preserve upstream comments and unrelated options.
  "ajvNotStrictMode": ["z.json", "kestra-2.9.0.json", "a.json"],
  "options": {"kestra-2.9.0.json": {"unknownKeywords": ["$dynamic"]}},
  "skiptest": ["other.json"]
}\n`);
    return directory;
}

function catalog(directory) {
    return JSON.parse(readFileSync(resolve(directory, "src/api/json/catalog.json"), "utf8")).schemas;
}

test("adds a newer version numerically and preserves existing registrations", t => {
    const directory = fixture(t);
    updateSchemaStore(directory, bundle, "v2.10.0");
    const [entry, other] = catalog(directory);
    assert.equal(entry.url, "https://www.schemastore.org/kestra-2.10.0.json");
    assert.deepEqual(Object.keys(entry.versions), ["2.9.0", "2.10.0"]);
    assert.deepEqual(entry.fileMatch, ["**/flows/*.yml"]);
    assert.equal(other.name, "Other schema");
    const config = readFileSync(resolve(directory, "src/schema-validation.jsonc"), "utf8");
    assert.match(config, /Preserve upstream comments/);
    assert.match(config, /"skiptest": \["other.json"\]/);
    assert.match(config, /kestra-2.10.0.json/);
    assert.ok(config.indexOf('"z.json"') < config.indexOf('"kestra-2.9.0.json"'));
    assert.ok(config.indexOf('"kestra-2.10.0.json"') < config.indexOf('"a.json"'));
    assert.match(config, /partial-time/);
    assert.match(config, /\$internalStorageURI/);
    assert.match(readFileSync(resolve(directory, "src/test/kestra-2.10.0/hello-world.yaml"), "utf8"), /io.kestra.plugin.core.log.Log/);
});

test("a maintenance release does not downgrade the default schema", t => {
    const directory = fixture(t);
    updateSchemaStore(directory, bundle, "v1.9.12");
    const [entry] = catalog(directory);
    assert.equal(entry.url, "https://www.schemastore.org/kestra-2.9.0.json");
    assert.equal(entry.versions["1.9.12"], "https://www.schemastore.org/kestra-1.9.12.json");
});

test("rerunning a release produces identical files", t => {
    const directory = fixture(t);
    updateSchemaStore(directory, bundle, "v2.10.0");
    const paths = ["src/api/json/catalog.json", "src/schema-validation.jsonc",
        "src/schemas/json/kestra-2.10.0.json", "src/test/kestra-2.10.0/hello-world.yaml"];
    const before = paths.map(path => readFileSync(resolve(directory, path), "utf8"));
    updateSchemaStore(directory, bundle, "v2.10.0");
    assert.deepEqual(paths.map(path => readFileSync(resolve(directory, path), "utf8")), before);
});

test("upstream configuration changes fail before modifying the catalog", t => {
    const directory = fixture(t);
    const before = readFileSync(resolve(directory, "src/api/json/catalog.json"), "utf8");
    writeFileSync(resolve(directory, "src/schema-validation.jsonc"), '{"ajvNotStrictMode":[],"options":{}}');
    assert.throws(() => updateSchemaStore(directory, bundle, "v2.10.0"), /configuration needs review/);
    assert.equal(readFileSync(resolve(directory, "src/api/json/catalog.json"), "utf8"), before);
});
