// @ts-check

/**
 * True for modules groups may capture. Excludes module-federation
 * virtual/wrapper modules, which must stay in the chunks MF assigns them, and
 * `?worker` wrapper stubs: tiny `new Worker(...)` shims that must stay next to
 * their importer or they eagerly drag their group's whole chunk in.
 * @param {string} id
 */
const isRealModule = (id) =>
    !id.startsWith("\0") &&
    !id.includes("virtual:") &&
    !id.includes("__mf") &&
    !id.includes("__loadShare__") &&
    !id.includes("@module-federation") &&
    !id.includes("?worker") &&
    // Implementations of MF shared singletons (see federation() shared config)
    // must keep their own chunks: consumers import them through MF loadShare
    // wrappers, and merging them with wrapper consumers creates a
    // wrapper <-> chunk evaluation cycle that crashes at boot.
    !/node_modules[\\/](vue|@vue)[\\/]/.test(id) &&
    // Both id forms: the bare-specifier resolution (@kestra-io/kestra-sdk) and
    // the workspace path (packages/kestra-sdk) it can resolve to.
    !/(@kestra-io|packages)[\\/]kestra-sdk/.test(id) &&
    // Topology imports the design system, so capturing it in the (eager) vendor
    // group would create a vendor <-> design-system chunk cycle; left to
    // automatic chunking it gets its own chunk with one-way edges.
    !/@kestra-io[\\/]topology[\\/]/.test(id)

/**
 * @param {RegExp} pattern
 * @returns {(id: string) => boolean}
 */
const matches = (pattern) => (id) => isRealModule(id) && pattern.test(id)

// Negative priorities keep module federation's own groups (priority >= 0)
// winning every module they target; recursion off so shared deps stay put.
const GROUPS = [
    // Monaco and the design-system editor files that statically import it.
    // Lazy: KsEditor is exported as an async component.
    {
        name: "monaco",
        test: matches(/node_modules[\\/](monaco-editor|monaco-yaml|monaco-worker-manager|monaco-marker-data-provider)[\\/]|design-system[\\/]src[\\/](components[\\/]Form[\\/]KsEditor\.vue|composables[\\/](useKsEditor|useEditor|useSuggestWidgetIcons|PlaceholderContentWidget)|utils[\\/]monacoSetup)/),
        priority: -10,
        includeDependenciesRecursively: false,
    },
    // The whole markdown/Shiki toolchain in a single lazy chunk
    // (KsMarkdown is exported as an async component).
    {
        name: "markdown",
        // Entries that look like typos are real unified-ecosystem package names.
        test: matches(/design-system[\\/]src[\\/]components[\\/]Data[\\/]KsMarkdown[\\/]|node_modules[\\/](shiki|@shikijs|oniguruma-to-es|regex(-recursion|-utilities)?|remark-[^\\/]+|micromark[^\\/]*|mdast[^\\/]*|unified|unist[^\\/]*|vfile[^\\/]*|hast[^\\/]*|devlop|ccount|character-[^\\/]+|decode-named-character-reference|markdown-table|longest-streak|trim-lines|zwitch|bail|trough|escape-string-regexp)[\\/]/), // codespell:ignore devlop,trough
        priority: -15,
        includeDependenciesRecursively: false,
    },
    // One design-system chunk (with element-plus, its foundation) so federated
    // plugin UIs can share it as a unit. Captures ALL of design-system/src:
    // its ids live under the symlinked node_modules path, so anything left out
    // falls into the vendor group and cycles back here (e.g. taskIcon.ts).
    // Editor files are not here only because the monaco group claims them first.
    {
        name: "design-system",
        test: matches(/design-system[\\/]src[\\/]|node_modules[\\/](element-plus|@element-plus)[\\/]/),
        priority: -20,
        includeDependenciesRecursively: false,
    },
    // Third-party code needed at startup, merged into a few files.
    {
        name: "vendor",
        test: matches(/node_modules/),
        tags: ["$initial"],
        priority: -30,
        includeDependenciesRecursively: false,
    },
    // Application code needed at startup, merged into a few files.
    {
        name: "app",
        test: isRealModule,
        tags: ["$initial"],
        priority: -40,
        includeDependenciesRecursively: false,
    },
]

/**
 * Consolidates the build output into few, purposeful chunks instead of
 * hundreds of tiny files.
 *
 * Must be registered after the federation plugin: MF strips user-declared
 * `codeSplitting.groups` in its `config` hook (grouping its init wrappers can
 * break remote init order), so this appends to the groups MF installed there.
 * @returns {import("vite").Plugin}
 */
export function consolidateChunks() {
    return {
        name: "kestra:consolidate-chunks",
        apply: "build",
        config(config) {
            const build = (config.build ??= {})
            const rolldown = (build.rolldownOptions ??= {})
            const outputs = [(rolldown.output ??= {})].flat()
            for (const output of outputs) {
                const existing = output.codeSplitting
                const codeSplitting = typeof existing === "object" && existing !== null ? existing : {}
                output.codeSplitting = codeSplitting
                ;(codeSplitting.groups ??= []).push(...GROUPS)
            }
        },
    }
}
