import {existsSync, readFileSync, writeFileSync} from "node:fs"

/**
 * Writes `nextContent` only if it differs from what is on disk by more than trailing whitespace,
 * and matches the file's existing final-newline style when it does.
 *
 * Without this the tooling is not formatting-neutral: it serialises with `JSON.stringify`, which
 * emits no final newline, while most editors add one when a developer touches a file by hand.
 * Every scheduled run then stripped those newlines back off and opened a PR whose entire diff was
 * `\ No newline at end of file` — see kestra-io/kestra#17823.
 *
 * @returns whether the file was written
 */
export function writeIfChanged(filePath: string, nextContent: string): boolean {
    if (!existsSync(filePath)) {
        writeFileSync(filePath, nextContent)
        return true
    }

    const currentContent = readFileSync(filePath, "utf-8")
    if (currentContent.trimEnd() === nextContent.trimEnd()) return false

    // Preserve whatever final-newline convention the file already uses, so a real content change
    // doesn't smuggle in an unrelated whitespace flip alongside it.
    const endsWithNewline = currentContent.endsWith("\n")
    writeFileSync(filePath, endsWithNewline ? `${nextContent.trimEnd()}\n` : nextContent.trimEnd())
    return true
}
