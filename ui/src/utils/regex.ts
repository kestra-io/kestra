const pebbleStart = "\\{\\{ *";
const fieldWithoutDotCapture = "([^\\(\\)}:~. ]*)(?![^\\(\\)}\\s])";
const dotAccessedFieldWithParentCapture = "([^\\(\\)},:~ ]+)\\." + fieldWithoutDotCapture;
const maybeTextFollowedBySeparator = "(?:[^~},: ]*[~ ]+)*";
const maybeParams = "((?:[^\\n\\(\\)~},:= ]+=[^\\n~},:= ]+(?: *, *)?)+)?['\"]?([^\\n\\(\\)~},:= ]*)?";
const functionWithMaybeParams = "([^\\n\\(\\)},:~ ]+)\\(" + maybeParams

export default {
    beforeSeparator: (additionalSeparators = []) => `([^}:\\s${additionalSeparators.join("")}]*)`,
    /** [fullMatch, dotForbiddenField] */
    capturePebbleVarRoot: `${pebbleStart}${maybeTextFollowedBySeparator}${fieldWithoutDotCapture}`,
    /** [fullMatch, parentFieldMaybeIncludingDots, childField] */
    capturePebbleVarParent: `${pebbleStart}${maybeTextFollowedBySeparator}${dotAccessedFieldWithParentCapture}`,
    /** [fullMatch, functionName, textBetweenParenthesis, maybeTypedWordStart] */
    capturePebbleFunction: `${pebbleStart}${maybeTextFollowedBySeparator}${functionWithMaybeParams}`,
    captureStringValue: "^[\"']([^\"']+)[\"']$"
}