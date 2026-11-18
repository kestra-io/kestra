const TAG_NAMES = new Set(["i18n-t", "I18nT"])

const isScope = (attribute) => attribute.directive
    ? attribute.key.name.name === "bind" && attribute.key.argument?.name === "scope"
    : attribute.key.name === "scope"

/**
 * vue-i18n defaults `<i18n-t>` to the parent scope and warns on every render when it falls back to
 * the global one, the only scope Kestra's messages live in; it has no setting to change that default.
 */
const i18nTScope = {
    meta: {
        type: "problem",
        docs: {description: "require scope=\"global\" on <i18n-t>"},
        fixable: "code",
        schema: [],
        messages: {missing: "Set scope=\"global\" on <i18n-t>."},
    },
    create(context) {
        const source = context.sourceCode
        return source.parserServices.defineTemplateBodyVisitor?.({
            VElement(element) {
                if (!TAG_NAMES.has(element.rawName)) return
                const {attributes} = element.startTag
                if (attributes.some(isScope)) return

                const last = attributes.at(-1)
                context.report({
                    node: element.startTag,
                    messageId: "missing",
                    fix(fixer) {
                        if (!last) return fixer.insertTextAfterRange([0, element.startTag.range[0] + 1 + element.rawName.length], " scope=\"global\"")
                        // A tag with one attribute per line gets the new one on its own line, at the same indent.
                        const multiline = last.loc.start.line !== element.startTag.loc.start.line
                        const separator = multiline ? `\n${" ".repeat(last.loc.start.column)}` : " "
                        return fixer.insertTextAfter(last, `${separator}scope="global"`)
                    },
                })
            },
        }) ?? {}
    },
}

export default {
    meta: {name: "kestra-i18n"},
    rules: {"i18n-t-scope": i18nTScope},
}
