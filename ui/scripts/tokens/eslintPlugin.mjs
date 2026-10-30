import {declarationsIn, knownTokens, undeclaredMessage, usagesIn} from "./knownTokens.mjs"

const url = "https://github.com/kestra-io/kestra/blob/develop/ui/scripts/tokens/README.md"

/**
 * The JavaScript half of the token check: `cssVar("--ks-…")` and any `var(--ks-…)` written inside a
 * string, which stylelint cannot see. Same failure either way — the name resolves to nothing and the
 * value is silently dropped (kestra-io/kestra#18777).
 */
const noUndeclaredKsToken = {
    meta: {
        type: "problem",
        docs: {description: "disallow --ks-* custom properties that are declared nowhere", url},
        schema: [],
        messages: {undeclared: "{{detail}}"},
    },
    create(context) {
        const source = context.sourceCode
        const known = new Set(knownTokens())
        declarationsIn(source.getText()).forEach(name => known.add(name))

        const report = (node, token, hasFallback, offset) => {
            if (known.has(token)) return
            const at = offset ?? source.getText(node).indexOf(token)
            const start = source.getLocFromIndex(node.range[0] + Math.max(at, 0))
            context.report({
                node,
                loc: at < 0 ? node.loc : {start, end: source.getLocFromIndex(node.range[0] + at + token.length)},
                messageId: "undeclared",
                data: {detail: undeclaredMessage(token, known, hasFallback)},
            })
        }

        // The offset is the one usagesIn found, so two occurrences of a token in one string do not
        // both point at the first; +1 skips the quote the raw text starts with.
        const reportUsages = (node, text) => {
            for (const {token, index, hasFallback} of usagesIn(text)) report(node, token, hasFallback, index + 1)
        }

        return {
            // `cssVar("--ks-…")` reads the property at runtime, so a name only it uses is never
            // declared in CSS and stylelint never sees it. A bare token-shaped string elsewhere is
            // left alone: it is as likely to be a declaration or a list of names as a lookup.
            CallExpression(node) {
                const callee = node.callee.type === "MemberExpression" ? node.callee.property : node.callee
                if (callee?.name !== "cssVar") return
                const [first] = node.arguments
                if (first?.type === "Literal" && typeof first.value === "string" && first.value.startsWith("--ks-")) {
                    report(first, first.value, false)
                }
            },
            Literal(node) {
                if (typeof node.value === "string") reportUsages(node, node.value)
            },
            TemplateElement(node) {
                reportUsages(node, node.value.raw)
            },
        }
    },
}

const TOKEN = /^--ks-[A-Za-z0-9-]+$/

/** The stylesheet half, for `@eslint/css`: every `var(--ks-…)` in `.css` and plain `<style>` blocks. */
const noUndeclaredCssToken = {
    meta: {
        type: "problem",
        docs: {description: "disallow var(--ks-*) in stylesheets when the token is declared nowhere", url},
        schema: [],
        messages: {undeclared: "{{detail}}"},
    },
    create(context) {
        const source = context.sourceCode
        const known = new Set(knownTokens())
        declarationsIn(source.getText()).forEach(name => known.add(name))

        const report = (token, hasFallback, loc) => {
            if (TOKEN.test(token) && !known.has(token)) {
                context.report({loc, messageId: "undeclared", data: {detail: undeclaredMessage(token, known, hasFallback)}})
            }
        }

        return {
            "Function[name=/^var$/i]"(node) {
                const [first, ...rest] = node.children
                if (first?.type !== "Identifier") return
                report(first.name, rest.some(child => child.type === "Operator" && child.value === ","), first.loc)
            },
            // CSSTree leaves custom property values and `var()` fallbacks unparsed, so their tokens are found by text.
            Raw(node) {
                const at = node.loc.start.offset
                for (const {token, index, hasFallback} of usagesIn(node.value)) {
                    report(token, hasFallback, {
                        start: source.getLocFromIndex(at + index),
                        end: source.getLocFromIndex(at + index + token.length),
                    })
                }
            },
        }
    },
}

export default {
    meta: {name: "kestra-tokens"},
    rules: {
        "no-undeclared-ks-token": noUndeclaredKsToken,
        "no-undeclared-css-token": noUndeclaredCssToken,
    },
}
