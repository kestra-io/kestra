// Equivalent spellings of the predefined relative ranges, normalized to the one the option lists
// and the API use - Java's Duration.parse, which the API applies, rejects the week designator.
const RELATIVE_DATE_ALIASES: Record<string, string> = {
    P1D: "PT24H",
    P2D: "PT48H",
    P7D: "PT168H",
    P1W: "PT168H",
    P30D: "PT720H",
    P365D: "PT8760H",
}

export const normalizeRelativeDate = (value: string): string => RELATIVE_DATE_ALIASES[value] ?? value
