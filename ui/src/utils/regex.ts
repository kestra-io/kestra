const maybeText = (allowSeparators: boolean) => "(?:\"[^\"]*\")|(?:'[^']*')|(?:(?:(?!\\}\\})" + (allowSeparators ? "[\\S\\n ]" : "[^~+,:\\n ]") + ")*)";
const maybeAnotherPebbleExpression = "(?:[\\n ]*\\{\\{[\\n ]*" + maybeText(true) + "[\\n ]*\\}\\}[\\n ]*)*";
const pebbleStart = "\\{\\{[\\n ]*";
const fieldWithoutDotCapture = "([^\\(\\)\\}:~+.\\n '\"]*)(?![^\\(\\)\\}\\n ])";
const dotAccessedFieldWithParentCapture = "([^\\(\\)\\}:~+\\n '\"]*)\\." + fieldWithoutDotCapture;
const maybeTextFollowedBySeparator = "(?:" + maybeText(false) + "[~+ ]+)*";
const paramKey = "[^\\n \\(\\)~+\\},:=]+";
const paramValue = "(?:(?:(?:\"[^\"]*\"?)|(?:'[^']*'?)|[^,)]))*";
const maybeParams = "(" +
    "(?:[\\n ]*" + paramKey + "[\\n ]*=[\\n ]*" + paramValue + "(?:[\\n ]*,[\\n ]*)?)+)?" +
    "([^\\n \\(\\)~+\\},:=]*)?";
const functionWithMaybeParams = "([^\\n\\(\\)\\},:~ ]+)\\(" + maybeParams

export default {
    beforeSeparator: (additionalSeparators: string[] = []) => `([^\\}:\\n ${additionalSeparators.join("")}]*)`,
    /** [fullMatch, dotForbiddenField] */
    capturePebbleVarRoot: `${maybeAnotherPebbleExpression}${pebbleStart}${maybeTextFollowedBySeparator}${fieldWithoutDotCapture}`,
    /** [fullMatch, parentFieldMaybeIncludingDots, childField] */
    capturePebbleVarParent: `${maybeAnotherPebbleExpression}${pebbleStart}${maybeTextFollowedBySeparator}${dotAccessedFieldWithParentCapture}`,
    /** [fullMatch, functionName, textBetweenParenthesis, maybeTypedWordStart] */
    capturePebbleFunction: `${maybeAnotherPebbleExpression}${pebbleStart}${maybeTextFollowedBySeparator}${functionWithMaybeParams}`,
    captureStringValue: "^[\"']([^\"']+)[\"']$"
}
