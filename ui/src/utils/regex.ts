const pebbleStart = "\\{\\{ *";
const fieldWithoutDotCapture = "([^}:~. ]*)(?![^}\\s])";
const dotAccessedFieldWithParentCapture = "([^}:~ ]+)\\." + fieldWithoutDotCapture;
const maybeTextFollowedBySeparator = "(?:[^~}: ]*[~ ]+)*";

export default {
    beforeSeparator: "([^}:\\s]*)",
    capturePebbleVarRoot: `${pebbleStart}${maybeTextFollowedBySeparator}${fieldWithoutDotCapture}`,
    capturePebbleVarParent: `${pebbleStart}${maybeTextFollowedBySeparator}${dotAccessedFieldWithParentCapture}`,
}