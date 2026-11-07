import {definePlugin, InlineStyleAnnotation, isInlineStyleAnnotation, type ExpressiveCodeLine, type ExpressiveCodePlugin} from "@expressive-code/core"
import {pebbleBlocks} from "./pebble.ts"
import {pebbleColors} from "./themes/index.ts"

type Columns = [from: number, to: number]

const YAML = new Set(["yaml", "yml"])

/**
 * Recolors the syntax annotations of `line` that fall under the Pebble `blocks` (sorted `[from, to)` columns),
 * splitting an annotation that straddles the edge of a block.
 */
function paintLine(line: ExpressiveCodeLine, blocks: Columns[], pebbleColorByVariant: (string | undefined)[]) {
    for (const annotation of line.getAnnotations()) {
        if (!isInlineStyleAnnotation(annotation) || annotation.styleVariantIndex === undefined || !annotation.inlineRange) {
            continue
        }
        const pebble = pebbleColorByVariant[annotation.styleVariantIndex]
        if (!pebble) {
            continue
        }
        const {columnStart, columnEnd} = annotation.inlineRange

        let firstBlock = 0
        let searchEnd = blocks.length
        while (firstBlock < searchEnd) {
            const middle = (firstBlock + searchEnd) >> 1
            if (blocks[middle]![1] <= columnStart) {
                firstBlock = middle + 1
            } else {
                searchEnd = middle
            }
        }
        if (firstBlock === blocks.length || blocks[firstBlock]![0] >= columnEnd) {
            continue
        }

        const [blockFrom, blockTo] = blocks[firstBlock]!
        if (blockFrom <= columnStart && blockTo >= columnEnd) {
            annotation.color = pebble
            continue
        }

        const parts: [from: number, to: number, color: string | undefined][] = []
        const addPart = (from: number, to: number, color: string | undefined) => {
            if (to > from) {
                parts.push([from, to, color])
            }
        }
        let cursor = columnStart
        for (let index = firstBlock; index < blocks.length && blocks[index]![0] < columnEnd; index++) {
            const [from, to] = blocks[index]!
            addPart(cursor, from, annotation.color)
            addPart(Math.max(cursor, from), Math.min(columnEnd, to), pebble)
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
 * Expressive Code plugin that paints Pebble blocks in YAML with each Kestra theme's Pebble color. It recolors the
 * syntax highlighter's own annotations instead of layering new ones, so it adds no render pass. Non-Kestra themes
 * are left as is.
 */
export function pluginPebble(): ExpressiveCodePlugin {
    return definePlugin({
        name: "Pebble",
        hooks: {
            postprocessAnalyzedCode: ({codeBlock, styleVariants}) => {
                if (!YAML.has(codeBlock.language)) {
                    return
                }
                const pebbleColorByVariant = styleVariants.map(variant => pebbleColors.get(variant.theme.name))
                if (pebbleColorByVariant.every(color => !color)) {
                    return
                }

                const lines = codeBlock.getLines()
                const blocksByLine = new Map<number, Columns[]>()
                let lineIndex = 0
                let lineOffset = 0
                for (const [start, end] of pebbleBlocks(codeBlock.code)) {
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
                            const blocks = blocksByLine.get(index) ?? []
                            blocks.push([from, to])
                            blocksByLine.set(index, blocks)
                        }
                        offset += length + 1
                    }
                }

                blocksByLine.forEach((blocks, index) => paintLine(lines[index]!, blocks, pebbleColorByVariant))
            },
        },
    })
}
