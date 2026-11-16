import {definePlugin, InlineStyleAnnotation, isInlineStyleAnnotation, type ExpressiveCodeLine, type ExpressiveCodePlugin} from "@expressive-code/core"
import {pebbleTokens, type PebbleRole} from "./pebble.ts"
import {syntaxColors} from "./themes/index.ts"
import type {SyntaxColors} from "./types.ts"

type Run = [from: number, to: number, role: PebbleRole]

const YAML = new Set(["yaml", "yml"])

/**
 * Recolors the syntax annotations of `line` that fall under the Pebble `runs` (sorted `[from, to)` columns, one
 * role each), splitting an annotation that straddles the edge of a run.
 */
function paintLine(line: ExpressiveCodeLine, runs: Run[], syntaxByVariant: (SyntaxColors | undefined)[]) {
    for (const annotation of line.getAnnotations()) {
        if (!isInlineStyleAnnotation(annotation) || annotation.styleVariantIndex === undefined || !annotation.inlineRange) {
            continue
        }
        const syntax = syntaxByVariant[annotation.styleVariantIndex]
        if (!syntax) {
            continue
        }
        const {columnStart, columnEnd} = annotation.inlineRange

        let firstRun = 0
        let searchEnd = runs.length
        while (firstRun < searchEnd) {
            const middle = (firstRun + searchEnd) >> 1
            if (runs[middle]![1] <= columnStart) {
                firstRun = middle + 1
            } else {
                searchEnd = middle
            }
        }
        if (firstRun === runs.length || runs[firstRun]![0] >= columnEnd) {
            continue
        }

        const [runFrom, runTo, runRole] = runs[firstRun]!
        if (runFrom <= columnStart && runTo >= columnEnd) {
            annotation.color = syntax[runRole]
            continue
        }

        const parts: [from: number, to: number, color: string | undefined][] = []
        const addPart = (from: number, to: number, color: string | undefined) => {
            if (to > from) {
                parts.push([from, to, color])
            }
        }
        let cursor = columnStart
        for (let index = firstRun; index < runs.length && runs[index]![0] < columnEnd; index++) {
            const [from, to, role] = runs[index]!
            addPart(cursor, from, annotation.color)
            addPart(Math.max(cursor, from), Math.min(columnEnd, to), syntax[role])
            cursor = Math.min(columnEnd, to)
        }
        addPart(cursor, columnEnd, annotation.color)

        const [first, ...rest] = parts
        const {bgColor, italic, bold, underline, strikethrough, styleVariantIndex, renderPhase} = annotation
        for (const [from, to, color] of rest) {
            line.addAnnotation(new InlineStyleAnnotation({
                color,
                bgColor,
                italic,
                bold,
                underline,
                strikethrough,
                styleVariantIndex,
                renderPhase,
                inlineRange: {columnStart: from, columnEnd: to},
            }))
        }

        const [firstFrom, firstTo, firstColor] = first!
        /** Narrowed in place, since `deleteAnnotation` scans the line and would make a line of many blocks quadratic. */
        Object.assign(annotation, {
            color: firstColor,
            inlineRange: {columnStart: firstFrom, columnEnd: firstTo},
        })
    }
}

/**
 * Expressive Code plugin that paints Pebble blocks in YAML with each Kestra theme's colors, one per token role. It
 * recolors the syntax highlighter's own annotations instead of layering new ones, so it adds no render pass.
 * Non-Kestra themes are left as is.
 */
export function pluginPebble(): ExpressiveCodePlugin {
    return definePlugin({
        name: "Pebble",
        hooks: {
            postprocessAnalyzedCode: ({codeBlock, styleVariants}) => {
                if (!YAML.has(codeBlock.language)) {
                    return
                }
                const syntaxByVariant = styleVariants.map(variant => syntaxColors.get(variant.theme.name))
                if (syntaxByVariant.every(syntax => !syntax)) {
                    return
                }

                const lines = codeBlock.getLines()
                const runsByLine = new Map<number, Run[]>()
                let lineIndex = 0
                let lineOffset = 0
                for (const [start, end, role] of pebbleTokens(codeBlock.code)) {
                    while (lineOffset + lines[lineIndex]!.text.length < start) {
                        lineOffset += lines[lineIndex]!.text.length + 1
                        lineIndex++
                    }

                    let offset = lineOffset
                    for (let index = lineIndex; index < lines.length && offset < end; index++) {
                        const length = lines[index]!.text.length
                        const from = Math.max(start, offset) - offset
                        const to = Math.min(end, offset + length) - offset
                        if (to > from) {
                            const runs = runsByLine.get(index) ?? []
                            runs.push([from, to, role])
                            runsByLine.set(index, runs)
                        }
                        offset += length + 1
                    }
                }

                runsByLine.forEach((runs, index) => paintLine(lines[index]!, runs, syntaxByVariant))
            },
        },
    })
}
