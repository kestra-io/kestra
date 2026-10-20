import {parse} from "@vue/compiler-sfc"

const ELEMENT = 1
const ATTRIBUTE = 6

const blank = (text) => text.replace(/[^\n]/g, " ")

// Vue's `v-bind(x)` is no CSS function, so it is read as an unknown custom property of the same length.
const maskVBind = (css) => css.replace(/v-bind\([^()]*\)/g, (call) => `var(--${"x".repeat(call.length - 7)})`)

const staticStyleAttributes = (node, found = []) => {
    if (node.type === ELEMENT) {
        for (const prop of node.props) {
            if (prop.type === ATTRIBUTE && prop.name === "style" && prop.value) found.push(prop)
        }
    }
    for (const child of node.children ?? []) staticStyleAttributes(child, found)
    return found
}

/**
 * The SFC with everything but plain-CSS `<style>` contents and static `style="…"` values blanked,
 * so offsets match the original and reports and fixes need no remapping.
 */
export const maskVueStyles = (text) => {
    const {descriptor} = parse(text, {sourceMap: false, ignoreEmpty: false})
    const kept = []
    for (const style of descriptor.styles) {
        if ((!style.lang || style.lang === "css") && !style.src) kept.push({start: style.loc.start.offset, end: style.loc.end.offset})
    }
    for (const attribute of descriptor.template?.ast ? staticStyleAttributes(descriptor.template.ast) : []) {
        const quote = attribute.value.loc.start.offset
        if (!`"'`.includes(text[quote]) || !attribute.value.content.trim()) continue
        kept.push({start: attribute.loc.start.offset, open: quote + 1, end: attribute.loc.end.offset})
    }
    if (kept.length === 0) return null

    let masked = ""
    let cursor = 0
    for (const {start, open, end} of kept.sort((a, b) => a.start - b.start)) {
        masked += blank(text.slice(cursor, start))
        // `style="…"` becomes `x{     …}`, so the value parses as a rule body at the same offsets.
        masked += open === undefined
            ? maskVBind(text.slice(start, end))
            : `x{${blank(text.slice(start + 2, open))}${text.slice(open, end - 1)}}`
        cursor = end
    }
    return masked + blank(text.slice(cursor))
}

/**
 * Wraps eslint-plugin-vue's processor and adds a second, CSS-only block for `@eslint/css`.
 * Block 0 keeps the vue processor's handling of template `eslint-disable` comments.
 */
export const vueWithStyles = (vueProcessor) => ({
    meta: {name: "vue-with-styles"},
    supportsAutofix: true,
    preprocess(text, filename) {
        const blocks = vueProcessor.preprocess(text, filename)
        const masked = maskVueStyles(text)
        return masked === null ? blocks : [...blocks, {text: masked, filename: "style.css"}]
    },
    postprocess([templateMessages, styleMessages = []], filename) {
        return [...vueProcessor.postprocess([templateMessages], filename), ...styleMessages]
    },
})
