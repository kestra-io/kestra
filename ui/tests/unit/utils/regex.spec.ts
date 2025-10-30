import {describe, expect, it} from "vitest"
import RegexProvider from "../../../src/utils/regex";

describe("Regex", () => {
    it("before separator", () => {
        expect(new RegExp(RegexProvider.beforeSeparator()).exec("a b")?.[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator()).exec("a}b")?.[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator()).exec("a:b")?.[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator()).exec("a\nb")?.[1]).eq("a");
        expect(new RegExp(RegexProvider.beforeSeparator()).exec("ab c")?.[1]).eq("ab");
    });

    it("capture pebble var root", () => {
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{a")?.[1]).eq("a");
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{a.b")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot).exec("{{a.b")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{.a")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{a}b")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{}a")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("a:{{b")?.[1]).eq("b");
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{a:b")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{:a")).toBeNull();
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{a~b")?.[1]).eq("b");
        expect(new RegExp(RegexProvider.capturePebbleVarRoot + "$").exec("{{~a")?.[1]).eq("a");
    });

    it("capture pebble var parent", () => {
        let nestedFieldMatcher = new RegExp(RegexProvider.capturePebbleVarParent + "$").exec("{{a.b");
        expect(nestedFieldMatcher?.[1]).eq("a");
        expect(nestedFieldMatcher?.[2]).eq("b");

        nestedFieldMatcher = new RegExp(RegexProvider.capturePebbleVarParent + "$").exec("a.b");
        expect(nestedFieldMatcher).toBeNull();

        nestedFieldMatcher = new RegExp(RegexProvider.capturePebbleVarParent + "$").exec("{{a ~ b.c");
        expect(nestedFieldMatcher?.[1]).eq("b");
        expect(nestedFieldMatcher?.[2]).eq("c");
    })

    it("capture pebble function", () => {
        let functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(") ?? [];
        expect([...functionMatcher]).toEqual(["{{myFunc(", "myFunc", undefined, undefined]);

        // Missing param value, no match
        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(myK") ?? [];
        expect([...functionMatcher]).toEqual(["{{myFunc(myK", "myFunc", undefined, "myK"]);

        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(my-param_1='value1'") ?? [];
        expect([...functionMatcher]).toEqual(["{{myFunc(my-param_1='value1'", "myFunc", "my-param_1='value1'", undefined]);

        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(my-param_1=myVar,") ?? [];
        expect([...functionMatcher]).toEqual(["{{myFunc(my-param_1=myVar,", "myFunc", "my-param_1=myVar,", undefined]);

        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(my-param_1='value1',") ?? [];
        expect([...functionMatcher]).toEqual(["{{myFunc(my-param_1='value1',", "myFunc", "my-param_1='value1',", undefined]);

        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(my-param_1='value1' , my-param_2=\"value2\",") ?? [];
        expect([...functionMatcher]).toEqual(["{{myFunc(my-param_1='value1' , my-param_2=\"value2\",", "myFunc", "my-param_1='value1' , my-param_2=\"value2\",", undefined]);

        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(my-param_1='value1', myK") ?? [];
        expect([...functionMatcher]).toEqual(["{{myFunc(my-param_1='value1', myK", "myFunc", "my-param_1='value1', ", "myK"]);

        functionMatcher = new RegExp(RegexProvider.capturePebbleFunction + "$").exec("{{myFunc(my-param_1='value1')}} {{mySecondFunc(second-func-param_1='secondFuncValue1', 'to") ?? [];
        expect([...functionMatcher]).toEqual(["{{myFunc(my-param_1='value1')}} {{mySecondFunc(second-func-param_1='secondFuncValue1', 'to", "mySecondFunc", "second-func-param_1='secondFuncValue1', ", "'to"]);
    })

    it("capture string value", () => {
        let stringMatcher: RegExpExecArray | [] | null = new RegExp(RegexProvider.captureStringValue).exec("'a'") ?? [];
        expect([...stringMatcher]).toEqual(["'a'", "a"]);

        stringMatcher = new RegExp(RegexProvider.captureStringValue).exec("\"a\"") ?? [];
        expect([...stringMatcher]).toEqual(["\"a\"", "a"]);

        stringMatcher = new RegExp(RegexProvider.captureStringValue).exec("a");
        expect(stringMatcher).toBeNull();
    })

    it("multiline function, avoid crashing", () => {
        const complexMultilineFunctionButClosedPebbleExpression = `id: breaking-ui
namespace: io.kestra.blx
description: "Upload multiple files to s3 sequentially"


tasks:
  - id: placeholder
    type: io.kestra.plugin.core.log.Log
    message: |-
        {{
          "to_entries[] | select(.key | startswith(\\"" +
          inputs.selector +
          "\\")) | (.key + \\"->\\" + .value)"
        }}
`
        const regex = new RegExp(RegexProvider.capturePebbleFunction + "$");
        expect(regex.exec(complexMultilineFunctionButClosedPebbleExpression)).eq(null);

        const shouldMatchLastFunction = `id: breaking-ui
namespace: io.kestra.blx
description: "Upload multiple files to s3 sequentially"


tasks:
  - id: placeholder
    type: io.kestra.plugin.core.log.Log
    message: |-
        {{
          "to_entries[] | select(.key | startswith(\\"" +
          inputs.selector +
          "\\")) | (.key + \\"->\\" + .value)"
        }} {{myFunc(my-param_1='value1', my-param_2="value2", myK`
        expect([...(regex.exec(shouldMatchLastFunction) ?? [])]).toEqual([
            `{{
          "to_entries[] | select(.key | startswith(\\"" +
          inputs.selector +
          "\\")) | (.key + \\"->\\" + .value)"
        }} {{myFunc(my-param_1='value1', my-param_2="value2", myK`,
            "myFunc",
        "my-param_1='value1', my-param_2=\"value2\", ",
            "myK",
        ]);
    })
})
