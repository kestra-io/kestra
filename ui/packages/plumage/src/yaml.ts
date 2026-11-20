/**
 * TextMate injection that scopes a YAML 1.1 boolean written as a mapping key (`on:`, `y:`, `off:`) as a key, as Kestra
 * reads it, where the YAML grammar gives it the boolean color. Load it with the YAML grammar: a grammar that has
 * already highlighted a block does not pick up an injection loaded after it.
 */
export const yamlKeysInjection = {
    name: "kestra-yaml-keys",
    scopeName: "kestra.yaml.keys",
    injectTo: ["source.yaml"],
    injectionSelector: "L:source.yaml -comment -string",
    patterns: [{
        match: "(?<![^\\s\\[{,])(?:[Yy]|yes|Yes|YES|[Nn]|no|No|NO|true|True|TRUE|false|False|FALSE|on|On|ON|off|Off|OFF)(?=[ \\t]*:(?:[ \\t]|$))",
        name: "entity.name.tag.yaml",
    }],
    repository: {},
}
