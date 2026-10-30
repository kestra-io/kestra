type Params = Record<string, unknown>
type Translate = (key: string, params: Params) => string

const PLACEHOLDER = "@@slot@@"

/** The translated text on each side of one `{slot}`; keep the call site on one line, Vue turns a line break there into a space. */
export function splitTranslation(t: Translate, key: string, slot: string, params: Params = {}): [string, string] {
    const [before = "", ...rest] = t(key, {...params, [slot]: PLACEHOLDER}).split(PLACEHOLDER)

    return [before, rest.join("")]
}
