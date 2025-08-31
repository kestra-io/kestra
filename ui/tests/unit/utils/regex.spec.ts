import {describe, expect, it} from "vitest"
import RegexProvider from "../../../src/utils/regex";

describe("Regex", () => {
    it("before separator", () => {
        expect(new RegExp(RegexProvider.beforeSeparator()).exec("a b")[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator()).exec("a}b")[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator()).exec("a:b")[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator()).exec("a\nb")[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator()).exec("ab c")[1]).eq("ab");
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

        nestedFieldMatcher = new RegExp(RegexProvider.capturePebbleVarParent + "$").exec("a.b");
        expect(nestedFieldMatcher).toBeNull();

        nestedFieldMatcher = new RegExp(RegexProvider.capturePebbleVarParent + "$").exec("{{a ~ b.c");
        expect(nestedFieldMatcher[1]).eq("b");
        expect(nestedFieldMatcher[2]).eq("c");
    })

    it("capture pebble function", () => {
        let functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(");
        expect([...functionMatcher]).toEqual(["{{myFunc(", "myFunc", undefined, undefined]);

        // Missing param value, no match
        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(myK");
        expect([...functionMatcher]).toEqual(["{{myFunc(myK", "myFunc", undefined, "myK"]);

        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(my-param_1='value1'");
        expect([...functionMatcher]).toEqual(["{{myFunc(my-param_1='value1'", "myFunc", "my-param_1='value1'", undefined]);

        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(my-param_1=myVar,");
        expect([...functionMatcher]).toEqual(["{{myFunc(my-param_1=myVar,", "myFunc", "my-param_1=myVar,", undefined]);

        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(my-param_1='value1',");
        expect([...functionMatcher]).toEqual(["{{myFunc(my-param_1='value1',", "myFunc", "my-param_1='value1',", undefined]);

        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(my-param_1='value1' , my-param_2=\"value2\",");
        expect([...functionMatcher]).toEqual(["{{myFunc(my-param_1='value1' , my-param_2=\"value2\",", "myFunc", "my-param_1='value1' , my-param_2=\"value2\",", undefined]);

        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(my-param_1='value1', myK");
        expect([...functionMatcher]).toEqual(["{{myFunc(my-param_1='value1', myK", "myFunc", "my-param_1='value1', ", "myK"]);
    })

    it("capture string value", () => {
        let stringMatcher = new RegExp(RegexProvider.captureStringValue).exec("'a'");
        expect([...stringMatcher]).toEqual(["'a'", "a"]);

        stringMatcher = new RegExp(RegexProvider.captureStringValue).exec("\"a\"");
        expect([...stringMatcher]).toEqual(["\"a\"", "a"]);

        stringMatcher = new RegExp(RegexProvider.captureStringValue).exec("a");
        expect(stringMatcher).toBeNull();
    })
})
