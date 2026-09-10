/** Splits on newlines, not commas, since the backend joins violations with `\n` and some messages (e.g. Jackson's "Unrecognized field" error) legitimately contain commas. */
export function splitValidationErrors(constraints?: string): string[] {
    return constraints?.split(/[\r\n]+/).map((line) => line.trim()).filter(Boolean) ?? []
}
