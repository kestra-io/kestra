import {describe, expect, it} from "vitest"
import RegexProvider from "../../../src/utils/regex";

describe("Regex", () => {
    it("before separator", () => {
        expect(new RegExp(RegexProvider.beforeSeparator).exec("a b")[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator).exec("a}b")[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator).exec("a:b")[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator).exec("a\nb")[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator).exec("ab c")[1]).eq("ab");
    });

    it("capture pebble var root", () => {
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{a")[1]).eq("a");
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{a.b")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot).exec("{{a.b")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{.a")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{a}b")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{}a")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("a:{{b")[1]).eq("b");
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{a:b")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{:a")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{a~b")[1]).eq("b");
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{~a")[1]).eq("a");
    });

    it("capture pebble var parent", () => {
        let nestedFieldMatcher = new RegExp(RegexProvider.capturePebbleVarParent + "$").exec("{{a.b");
        expect(nestedFieldMatcher[1]).eq("a");
        expect(nestedFieldMatcher[2]).eq("b");

        nestedFieldMatcher = new RegExp(RegexProvider.capturePebbleVarParent + "$").exec("{{a ~ b.c");
        expect(nestedFieldMatcher[1]).eq("b");
        expect(nestedFieldMatcher[2]).eq("c");
    })
})
