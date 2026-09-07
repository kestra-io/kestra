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

        const report = (node, token) => {
            if (known.has(token)) return
            const raw = source.getText(node)
            const offset = raw.indexOf(token)
            const start = source.getLocFromIndex(node.range[0] + Math.max(offset, 0))
            context.report({
                node,
                loc: offset < 0 ? node.loc : {start, end: source.getLocFromIndex(node.range[0] + offset + token.length)},
                messageId: "undeclared",
                data: {detail: undeclaredMessage(token, known)},
            })
        }

        const reportUsages = (node, text) => {
            for (const {token} of usagesIn(text)) report(node, token)
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
                    report(first, first.value.toLowerCase())
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

export default {
    meta: {name: "kestra-tokens"},
    rules: {"no-undeclared-ks-token": noUndeclaredKsToken},
}
