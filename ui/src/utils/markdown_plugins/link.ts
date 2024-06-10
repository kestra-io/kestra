import MarkdownIt from "markdown-it";

export function linkTag(md: MarkdownIt) {
    const linkTagRegex = /\[\[link(\s+(.*?))?\]\]/g;

    md.inline.ruler.after("emphasis", "link_custom", (state, silent) => {
        if (silent) return false;

        // Check if the current position is a link tag
        const match = state.src.match(linkTagRegex);
        if (!match) return false;

        console.log(match)
        // Get the attributes, they must have the following format: key="value"
        const attrs = match[0].match(/\S+="(\S)+"/g)
            .map(attr => attr.split("="))
            .map(([name, value]) => [name, value.replace(/"/g, "")]);

        const token = state.push("link_custom_open", "link", 1);
        token.markup = "[[link]]";
        token.attrs = attrs;
        state.push("link_custom_close", "link_custom", -1);

        state.pos = state.pos + match[0].length;

        return true;
    });

    md.renderer.rules.link_custom_open = (tokens, idx) => {
        const token = tokens[idx];
        const attrs = token.attrs ? token.attrs.map(([name, value]) => `${name}="${value}"`).join("") : "";

        return `<router-md ${attrs}>`;
    };
    md.renderer.rules.link_custom_close = () => "</router-md>";
}