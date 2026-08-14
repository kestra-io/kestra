const pebbleStart = "\\{\\{[\\n ]*"
const fieldWithoutDotCapture = "([^()}:~+.\\n '\"]*)(?![^()}\\n ])"
const dotAccessedFieldWithParentCapture = "([^()}:~+\\n '\"]*)\\." + fieldWithoutDotCapture
const skippedTermToken = "(?:\"[^\"]*\")|(?:'[^']*')|[^\"'~+}]|(?:\\}(?!\\}))"
const termSeparator = "[~+]|(?:\\}\\}[\\n ]*\\{\\{)"
const maybeTextFollowedBySeparator = "(?:(?:" + skippedTermToken + ")*(?:" + termSeparator + "))*[\\n ]*"
const paramKey = "[^\\n ()~+},:=]+"
const paramValue = "(?:(?:\"[^\"]*\")|(?:'[^']*')|[^,)}\"'])*(?:\"[^\"]*|'[^']*)?"
const maybeParams = "(" +
    "(?:[\\n ]*" + paramKey + "[\\n ]*=[\\n ]*" + paramValue + "(?:[\\n ]*,[\\n ]*)?)+)?" +
    "([^\\n ()~+},:=]*)?"
const functionWithMaybeParams = "([^\\n()},:~ ]+)\\(" + maybeParams

export default {
    beforeSeparator: (additionalSeparators: string[] = []) => `([^\\}:\\n ${additionalSeparators.join("")}]*)`,
    /** [fullMatch, dotForbiddenField] */
    capturePebbleVarRoot: `${pebbleStart}${maybeTextFollowedBySeparator}${fieldWithoutDotCapture}`,
    /** [fullMatch, parentFieldMaybeIncludingDots, childField] */
    capturePebbleVarParent: `${pebbleStart}${maybeTextFollowedBySeparator}${dotAccessedFieldWithParentCapture}`,
    /** [fullMatch, functionName, textBetweenParenthesis, maybeTypedWordStart] */
    capturePebbleFunction: `${pebbleStart}${maybeTextFollowedBySeparator}${functionWithMaybeParams}`,
    captureStringValue: "^[\"']([^\"']+)[\"']$",
}
