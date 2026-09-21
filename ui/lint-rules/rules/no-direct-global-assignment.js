import {WATCHED_GLOBALS, createViImportTracker, globalPropertyName} from "../utils.js"

export default {
    meta: {
        type: "problem",
        fixable: "code",
        schema: [],
        docs: {description: "Stub globals with vi.stubGlobal so they can be restored"},
        messages: {
            direct: "Assigning to {{object}}.{{name}} replaces the global for every later spec file (the unit suite runs with isolate: false). Use vi.stubGlobal(\"{{name}}\", …) and vi.unstubAllGlobals() in afterAll.",
        },
    },
    create(context) {
        const viImport = createViImportTracker()

        return {
            ImportDeclaration: viImport.visitImport,
            AssignmentExpression(node) {
                if ("=" !== node.operator) return
                const name = globalPropertyName(node.left)
                if (!name || !WATCHED_GLOBALS.has(name)) return

                context.report({
                    node,
                    messageId: "direct",
                    data: {object: node.left.object.name, name},
                    // Only rewrite when `vi` is already in scope; adding the import too
                    // would be a second, unrelated edit to the file.
                    fix: viImport.isImported()
                        ? (fixer) => fixer.replaceText(node, `vi.stubGlobal("${name}", ${context.sourceCode.getText(node.right)})`)
                        : undefined,
                })
            },
        }
    },
}
