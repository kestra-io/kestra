import {registerHooks} from "node:module"

// Tools still on the TS 6 compiler API get @typescript/typescript6; TS 7.1 only exports `version` from its root.
const url = import.meta.resolve("@typescript/typescript6")

registerHooks({
    resolve: (specifier, context, nextResolve) =>
        specifier === "typescript" ? {url, format: "commonjs", shortCircuit: true} : nextResolve(specifier, context),
})
