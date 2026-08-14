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
    !/(@kestra-io|packages)[\\/]topology[\\/]/.test(id)

/**
 * @param {RegExp} pattern
 * @returns {(id: string) => boolean}
 */
const matches = (pattern) => (id) => isRealModule(id) && pattern.test(id)

const MONACO_MODULES = /node_modules[\\/](monaco-editor|monaco-yaml|monaco-worker-manager|monaco-marker-data-provider)[\\/]|design-system[\\/]src[\\/](components[\\/]Form[\\/]KsEditor\.vue|composables[\\/](useKsEditor|useEditor|useSuggestWidgetIcons|PlaceholderContentWidget)|utils[\\/]monacoSetup)/
// Entries that look like typos are real unified-ecosystem package names.
const MARKDOWN_MODULES = /design-system[\\/]src[\\/]components[\\/]Data[\\/]KsMarkdown[\\/]|node_modules[\\/](shiki|@shikijs|oniguruma-to-es|regex(-recursion|-utilities)?|remark-[^\\/]+|micromark[^\\/]*|mdast[^\\/]*|unified|unist[^\\/]*|vfile[^\\/]*|hast[^\\/]*|devlop|ccount|character-[^\\/]+|decode-named-character-reference|markdown-table|longest-streak|trim-lines|zwitch|bail|trough|escape-string-regexp)[\\/]/ // codespell:ignore devlop,trough

/** Lazily loaded toolchains, each owning the private subtree of what it matches. */
const LAZY_GROUPS = [
    {name: "monaco", pattern: MONACO_MODULES},
    {name: "markdown", pattern: MARKDOWN_MODULES},
]

/**
 * Everything a lazy group's own modules statically reach that the eager graph
 * does not, keyed by group name. Filled during buildEnd.
 *
 * A group must take the whole private subtree of what it claims. Whatever it
 * leaves behind lands in some other chunk, and since the group chunk holds the
 * modules importing it, the two chunks end up importing each other. Such a cycle
 * builds cleanly and then throws ("X is not a function") the first time the
 * chunk loads, so it surfaces as a silently blank editor or markdown surface
 * rather than as a build failure.
 * @type {Map<string, Set<string>>}
 */
const lazySubtrees = new Map()

/**
 * @param {import("rolldown").PluginContext} ctx
 * @param {string[]} roots
 */
function staticClosure(ctx, roots) {
    const seen = new Set()
    const queue = [...roots]
    while (queue.length) {
        const id = queue.pop()
        if (!id || seen.has(id)) continue
        seen.add(id)
        // Static edges only: a dynamic import gets its own chunk, so its target
        // is not part of the subtree this group has to keep together.
        queue.push(...(ctx.getModuleInfo(id)?.importedIds ?? []))
    }
    return seen
}

/**
 * @param {import("rolldown").PluginContext} ctx
 */
function collectLazySubtrees(ctx) {
    lazySubtrees.clear()
    const ids = [...ctx.getModuleIds()]
    const eager = staticClosure(ctx, ids.filter((id) => ctx.getModuleInfo(id)?.isEntry))

    for (const {name, pattern} of LAZY_GROUPS) {
        // The other lazy group's declared modules are its own to place; swallowing
        // them here would make one lazy chunk statically pull in the other.
        const others = LAZY_GROUPS.filter((group) => group.name !== name)
        const subtree = new Set()
        for (const id of staticClosure(ctx, ids.filter((candidate) => isRealModule(candidate) && pattern.test(candidate)))) {
            if (eager.has(id) || !isRealModule(id)) continue
            if (others.some((group) => group.pattern.test(id))) continue
            subtree.add(id)
        }
        lazySubtrees.set(name, subtree)
    }
}

/**
 * Matches the group's declared modules plus everything privately reachable from
 * them, so no dependency of theirs is left stranded in another chunk.
 * @param {string} name
 * @param {RegExp} pattern
 * @returns {(id: string) => boolean}
 */
const matchesWithLazySubtree = (name, pattern) => (id) =>
    isRealModule(id) && (pattern.test(id) || lazySubtrees.get(name)?.has(id) === true)

const LANG_MODULE = /node_modules[\\/](@shikijs[\\/]langs|shiki[\\/]dist[\\/]langs)[\\/]/
const LANG_REGISTRY = /node_modules[\\/]shiki[\\/]dist[\\/]langs(-bundle-full-[^\\/]+)?\.mjs$/

/**
 * Grammars some module statically imports (shikiHighlighter.ts pre-registers a
 * set, shikiToolset.ts another), plus the ones those pull in transitively — html
 * embeds css and javascript, and so on. Filled during buildEnd: derived from the
 * graph rather than a hardcoded list, because one missed grammar turns the whole
 * on-demand bundle into a static import of whichever chunk needed it.
 * @type {Set<string>}
 */
const staticLangs = new Set()

/**
 * @param {import("rolldown").PluginContext} ctx
 */
function collectStaticLangs(ctx) {
    staticLangs.clear()
    const queue = []

    // Seed: grammars pulled in by ordinary modules. The registry index reaches
    // its grammars through dynamic imports, so it contributes nothing here.
    for (const id of ctx.getModuleIds()) {
        if (LANG_MODULE.test(id)) continue
        for (const imported of ctx.getModuleInfo(id)?.importedIds ?? []) {
            if (LANG_MODULE.test(imported)) queue.push(imported)
        }
    }

    while (queue.length) {
        const id = queue.pop()
        if (!id || staticLangs.has(id)) continue
        staticLangs.add(id)
        queue.push(...(ctx.getModuleInfo(id)?.importedIds ?? []).filter((dep) => LANG_MODULE.test(dep)))
    }
}

// Negative priorities keep module federation's own groups (priority >= 0)
// winning every module they target; recursion off so shared deps stay put.
const GROUPS = [
    // Monaco and the design-system editor files that statically import it.
    // Lazy: KsEditor is exported as an async component.
    {
        name: "monaco",
        test: matchesWithLazySubtree("monaco", MONACO_MODULES),
        priority: -10,
        includeDependenciesRecursively: false,
    },
    // Every grammar that is not pre-registered, in one chunk fetched only when a
    // code fence uses an exotic language (see loadLanguageOnDemand). Without
    // this group Shiki's registry emits one chunk per language (~350 files).
    {
        name: "shiki-langs",
        test: (id) => isRealModule(id) &&
            (LANG_REGISTRY.test(id) || (LANG_MODULE.test(id) && !staticLangs.has(id))),
        priority: -12,
        includeDependenciesRecursively: false,
    },
    // The whole markdown/Shiki toolchain in a single lazy chunk
    // (KsMarkdown is exported as an async component).
    {
        name: "markdown",
        test: matchesWithLazySubtree("markdown", MARKDOWN_MODULES),
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
        buildEnd() {
            collectStaticLangs(this)
            collectLazySubtrees(this)
        },
        generateBundle(_options, bundle) {
            // A static edge into shiki-langs would make every markdown render
            // fetch all ~350 grammars, which is exactly what this split avoids.
            const isLangChunk = (name) => /(^|[\\/])shiki-langs-/.test(name)
            for (const [name, chunk] of Object.entries(bundle)) {
                if (chunk.type !== "chunk" || isLangChunk(name)) continue
                const leaked = (chunk.imports ?? []).filter(isLangChunk)
                if (leaked.length) {
                    this.error(`Chunk '${name}' statically imports the on-demand grammar bundle (${leaked.join(", ")}). A statically imported grammar likely gained a transitive import that collectStaticLangs failed to reach.`)
                }
            }
        },
    }
}
