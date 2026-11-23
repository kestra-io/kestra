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

const maskRange = (text, {start, open, end}) => blank(text.slice(0, start))
    // `style="…"` becomes `x{     …}`, so the value parses as a rule body at the same offsets.
    + (open === undefined ? maskVBind(text.slice(start, end)) : `x{${blank(text.slice(start + 2, open))}${text.slice(open, end - 1)}}`)
    + blank(text.slice(end))

/**
 * One copy of the SFC per plain-CSS `<style>` block or static `style="…"` value, everything else
 * blanked, so offsets match the original and each is linted as its own stylesheet.
 */
export const maskVueStyles = (text) => {
    const {descriptor} = parse(text, {sourceMap: false, ignoreEmpty: false})
    const kept = []
    for (const style of descriptor.styles) {
        if ((!style.lang || style.lang === "css") && !style.src) kept.push({start: style.loc.start.offset, end: style.loc.end.offset})
    }
    for (const attribute of descriptor.template?.ast ? staticStyleAttributes(descriptor.template.ast) : []) {
        const quote = attribute.value.loc.start.offset
        if (!/["']/.test(text[quote]) || !attribute.value.content.trim()) continue
        kept.push({start: attribute.loc.start.offset, open: quote + 1, end: attribute.loc.end.offset})
    }
    return kept.sort((a, b) => a.start - b.start).map((range) => maskRange(text, range))
}

/**
 * Wraps eslint-plugin-vue's processor and adds one CSS-only block per stylesheet for `@eslint/css`.
 * Block 0 keeps the vue processor's handling of template `eslint-disable` comments.
 */
export const vueWithStyles = (vueProcessor) => ({
    meta: {name: "vue-with-styles"},
    supportsAutofix: true,
    preprocess(text, filename) {
        const styles = maskVueStyles(text).map((masked, index) => ({text: masked, filename: `style-${index}.css`}))
        return [...vueProcessor.preprocess(text, filename), ...styles]
    },
    postprocess([templateMessages, ...styleMessages], filename) {
        return [...vueProcessor.postprocess([templateMessages], filename), ...styleMessages.flat()]
    },
})
