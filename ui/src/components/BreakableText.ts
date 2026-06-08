import {h, type FunctionalComponent} from "vue"

const BreakableText: FunctionalComponent<{value?: string}> = (props) =>
    (props.value ?? "")
        .split(".")
        .flatMap((segment, index) => (index === 0 ? [segment] : [h("wbr"), "." + segment]))

BreakableText.props = {value: {type: String, default: ""}}

export default BreakableText
