// @ts-check
import valueParser from "postcss-value-parser";
import stylelint from "stylelint";

const {
  createPlugin,
  utils: {
    report,
    ruleMessages,
    validateOptions,
},
} = stylelint;

const ruleName = "ks/custom-property-pattern-usage";

const messages = ruleMessages(ruleName, {
	expected: (propName, pattern) => `Expected "${propName}" to match pattern "${pattern}"`,
});

const VAR_FUNC_REGEX = /var\(/i;

const isCustomProperty = (prop) => prop.startsWith("--");
function isRegExp(value) {
	return value instanceof RegExp;
}
function isString(value) {
	return value && typeof value === "string";
}

function isVarFunction(node) {
	return node.type === "function" && node.value.toLowerCase() === "var";
}

/** @type {import('stylelint').Rule} */
const rule = (primary) => {
	return (root, result) => {
		const validOptions = validateOptions(result, ruleName, {
			actual: primary,
			possible: [isRegExp, isString],
		});

		if (!validOptions) {
			return;
		}

        const regexpPattern = isString(primary) ? new RegExp(primary) : primary;

		/**
		 * @param {string} property
		 * @returns {boolean}
		 */
		function check(property) {
			return !isCustomProperty(property) || regexpPattern.test(property);
		}

		root.walkDecls((decl) => {
			const {value} = decl;

			if (VAR_FUNC_REGEX.test(value)) {
				const parsedValue = valueParser(value);

				parsedValue.walk((node) => {
					if (!isVarFunction(node)) return;

					// @ts-expect-error missing type
					const {nodes} = node;

					const firstNode = nodes[0];

					if (!firstNode || check(firstNode.value)) return;

					complain(declarationValueIndex(decl) + firstNode.sourceIndex, firstNode.value, decl);
				});
			}
		});

		/**
		 * @param {number} index
		 * @param {string} propName
		 * @param {import('postcss').Declaration} decl
		 */
		function complain(index, propName, decl) {
			report({
				result,
				ruleName,
				message: messages.expected,
				messageArgs: [propName, primary],
				node: decl,
				index,
				endIndex: index + propName.length,
			});
		}
	};
};

rule.ruleName = ruleName;
rule.messages = messages;

export default createPlugin(ruleName, rule);

function declarationBetweenIndex(decl) {
	const {prop} = decl.raws;
	const propIsObject = typeof prop === "object";

	return countChars([
		propIsObject && "prefix" in prop && prop.prefix,
		(propIsObject && "raw" in prop && prop.raw) || decl.prop,
		propIsObject && "suffix" in prop && prop.suffix,
	]);
}

function declarationValueIndex(decl) {
	const {between, value} = decl.raws;

	return (
		declarationBetweenIndex(decl) +
		countChars([between || ":", value && "prefix" in value && value.prefix])
	);
}

function countChars(values) {
	return values.reduce((/** @type {number} */ count, value) => {
		if (isString(value)) return count + value.length;

		return count;
	}, 0);
}