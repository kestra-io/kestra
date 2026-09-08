// @ts-check
import valueParser from "postcss-value-parser"
import stylelint from "stylelint"
import {declarationsIn, knownTokens, undeclaredMessage} from "./knownTokens.mjs"

const {
	createPlugin,
	utils: {report, ruleMessages, validateOptions},
} = stylelint

const VAR_FUNC_REGEX = /var\(/i

/** @param {unknown} value */
function isString(value) {
	return value && typeof value === "string"
}

/** @param {{type: string, value: string}} node */
function isVarFunction(node) {
	return node.type === "function" && node.value.toLowerCase() === "var"
}

const undeclaredRuleName = "ks/no-undeclared-custom-property"

const undeclaredMessages = ruleMessages(undeclaredRuleName, {
	rejected: (/** @type {string} */ message) => message,
})

const TOKEN_REGEX = /^--ks-[A-Za-z0-9-]+$/

/**
 * Reports a `var(--ks-…)` whose name is declared neither in the Figma palette nor anywhere in the
 * repository. Such a property is invalid at computed-value time, so the browser drops the whole
 * declaration and the element inherits instead, with nothing logged — kestra-io/kestra#18777.
 *
 * A name assembled by interpolation (`var(--ks-status-#{$state})`) does not match TOKEN_REGEX and is
 * left alone: its value is only known at runtime.
 *
 * @type {import('stylelint').Rule}
 */
const undeclaredRule = (primary) => {
	return (root, result) => {
		if (!validateOptions(result, undeclaredRuleName, {actual: primary, possible: [true]})) return

		// The file in hand may declare tokens of its own, and in an editor it is unsaved, so its
		// declarations are read from this copy rather than from the one the repository scan saw.
		const known = new Set(knownTokens())
		declarationsIn(root.toString()).forEach((/** @type {string} */ name) => known.add(name))

		/**
		 * @param {import('postcss').Node} node
		 * @param {string} value
		 * @param {number} offset
		 */
		function check(node, value, offset) {
			if (!VAR_FUNC_REGEX.test(value)) return

			valueParser(value).walk((parsed) => {
				if (!isVarFunction(parsed)) return

				// @ts-expect-error missing type
				const {nodes} = parsed
				const first = nodes[0]
				const token = first && isString(first.value) ? first.value : ""

				if (!TOKEN_REGEX.test(token) || known.has(token)) return

				const hasFallback = nodes.some((/** @type {{type: string}} */ n) => n.type === "div" && n.value === ",")

				report({
					result,
					ruleName: undeclaredRuleName,
					node,
					index: offset + first.sourceIndex,
					endIndex: offset + first.sourceIndex + token.length,
					message: undeclaredMessages.rejected(undeclaredMessage(token, known, hasFallback)),
				})
			})
		}

		root.walkDecls((decl) => check(decl, decl.value, declarationValueIndex(decl)))
		root.walkAtRules((atRule) => check(atRule, atRule.params, atRule.name.length + 1 + countChars([atRule.raws.afterName || " "])))
	}
}

undeclaredRule.ruleName = undeclaredRuleName
undeclaredRule.messages = undeclaredMessages


export default createPlugin(undeclaredRuleName, undeclaredRule)

function declarationBetweenIndex(decl) {
	const {prop} = decl.raws
	const propIsObject = typeof prop === "object"

	return countChars([
		propIsObject && "prefix" in prop && prop.prefix,
		(propIsObject && "raw" in prop && prop.raw) || decl.prop,
		propIsObject && "suffix" in prop && prop.suffix,
	])
}

function declarationValueIndex(decl) {
	const {between, value} = decl.raws

	return (
		declarationBetweenIndex(decl) +
		countChars([between || ":", value && "prefix" in value && value.prefix])
	)
}

function countChars(values) {
	return values.reduce((/** @type {number} */ count, value) => {
		if (isString(value)) return count + value.length

		return count
	}, 0)
}