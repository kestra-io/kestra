import {describe, expect, it} from "vitest"
import {pebbleBlocks, pebbleTokens} from "./pebble.ts"
import {yamlCorpus} from "../tests/corpus.ts"

const paint = (text: string) => pebbleTokens(text).map(([start, end, role]) => `${role}:${text.slice(start, end)}`)

describe("pebbleTokens", () => {
    it.each(yamlCorpus().map(file => [file.path, file] as const))("covers exactly the Pebble blocks of %s, without gaps or overlaps", (_, {code}) => {
        const tokens = pebbleTokens(code)
        let next = 0
        for (const [start, end] of pebbleBlocks(code)) {
            let cursor = start
            while (cursor < end) {
                const token = tokens[next++]
                expect(token?.[0]).toBe(cursor)
                cursor = token![1]
            }
            expect(cursor).toBe(end)
        }
        expect(next).toBe(tokens.length)
    })

    it("paints a variable path in its own color, apart from the delimiters", () => {
        expect(paint("a: {{ inputs.name }} {{- vars.x -}}")).toEqual([
            "pebble:{{ ", "pebbleVariable:inputs.name", "pebble: }}",
            "pebble:{{- ", "pebbleVariable:vars.x", "pebble: -}}",
        ])
    })

    it("colors keywords, constants, strings and numbers", () => {
        expect(paint("{% if x == 1.5 and y is null %}{{ 'a' ~ \"b\\\"c\" }}{% endif %}")).toEqual([
            "pebble:{% ", "keyword:if", "pebble: ", "pebbleVariable:x", "pebble: ", "operator:==", "pebble: ", "number:1.5", "pebble: ", "keyword:and", "pebble: ", "pebbleVariable:y", "pebble: ", "keyword:is", "pebble: ", "constant:null", "pebble: %}",
            "pebble:{{ ", "string:'a'", "pebble: ", "operator:~", "pebble: ", "string:\"b\\\"c\"", "pebble: }}",
            "pebble:{% ", "keyword:endif", "pebble: %}",
        ])
    })

    it("colors a call and a filter as functions, with their arguments", () => {
        expect(paint("{{ now() | date('yyyy', timeZone=tz) }}")).toEqual([
            "pebble:{{ ", "function:now", "punctuation:()", "pebble: ", "operator:|", "pebble: ", "function:date", "punctuation:(", "string:'yyyy'", "punctuation:,", "pebble: ", "pebbleVariable:timeZone", "operator:=", "pebbleVariable:tz", "punctuation:)", "pebble: }}",
        ])
    })

    it("keeps a keyword used as a member or a filter from being a keyword", () => {
        expect(paint("{{ x.if | not }} {{ in.for }} {{ true.x }}")).toEqual([
            "pebble:{{ ", "pebbleVariable:x.if", "pebble: ", "operator:|", "pebble: ", "function:not", "pebble: }}",
            "pebble:{{ ", "pebbleVariable:in.for", "pebble: }}",
            "pebble:{{ ", "pebbleVariable:true.x", "pebble: }}",
        ])
    })

    it("colors brackets, maps and ranges", () => {
        expect(paint("{{ outputs['t'].v ?? {'a': [1..3]} }}")).toEqual([
            "pebble:{{ ", "pebbleVariable:outputs", "punctuation:[", "string:'t'", "punctuation:]", "pebbleVariable:.v", "pebble: ", "operator:??", "pebble: ", "punctuation:{", "string:'a'", "punctuation::", "pebble: ", "punctuation:[", "number:1", "operator:..", "number:3", "punctuation:]}", "pebble: }}",
        ])
    })

    it("gives an empty trim block one token", () => {
        expect(paint("x: {{-}}")).toEqual(["pebble:{{-}}"])
    })

    it("reads an accented name whole, as the grammar does", () => {
        expect(paint("{{ forçado ?? orçamento() | inégal }} {{ trueño }} {{ éif }}")).toEqual([
            "pebble:{{ ", "pebbleVariable:forçado", "pebble: ", "operator:??", "pebble: ", "function:orçamento", "punctuation:()", "pebble: ", "operator:|", "pebble: ", "function:inégal", "pebble: }}",
            "pebble:{{ ", "pebbleVariable:trueño", "pebble: }}",
            "pebble:{{ é", "pebbleVariable:if", "pebble: }}",
        ])
    })

    it("keeps a filter its function color when the pipe wraps to the next line", () => {
        expect(paint("x: {{ inputs.list |\n    upper }}")).toEqual(["pebble:{{ ", "pebbleVariable:inputs.list", "pebble: ", "operator:|", "pebble:\n    ", "function:upper", "pebble: }}"])
    })

    it("stops a string at the closer the block ends on, as the block does", () => {
        expect(paint("x: '{{ \"a}}b\" }}'")).toEqual(["pebble:{{ \"", "pebbleVariable:a", "pebble:}}"])
    })

    it("leaves an unclosed block in the Pebble color except for what it can read", () => {
        expect(paint("x: {{ inputs.a | json\ny: 1")).toEqual(["pebble:{{ ", "pebbleVariable:inputs.a", "pebble: ", "operator:|", "pebble: ", "function:json"])
        expect(paint("x: {{ 'open\n  y: 1")).toEqual(["pebble:{{ '", "pebbleVariable:open", "pebble:\n  ", "pebbleVariable:y", "punctuation::", "pebble: ", "number:1"])
    })
})
