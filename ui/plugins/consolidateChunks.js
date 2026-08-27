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
    // Implementations of MF shared singletons (see federation() shared config).
    // Merging them with wrapper *consumers* creates a wrapper <-> chunk
    // evaluation cycle that crashes at boot, so the eager groups may not have
    // them; the mf-shared group claims them alongside their own shims.
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

/**
 * The shims MF generates per shared module: the async init wrapper, and the
 * prebuild proxy re-exporting the same implementation.
 */
const isShareShim = (id) => id.includes("__loadShare__") || id.includes("__prebuild__")

/** MF's own virtual modules: the shims, the share map, the remote entry. */
const isMfInternal = (id) =>
    id.startsWith("\0") || id.includes("virtual:") || id.includes("__mf") || id.includes("@module-federation")

const MONACO_MODULES = /node_modules[\\/](monaco-editor|monaco-yaml|monaco-worker-manager|monaco-marker-data-provider)[\\/]|design-system[\\/]src[\\/](components[\\/]Form[\\/]KsEditor\.vue|composables[\\/](useKsEditor|useEditor|useSuggestWidgetIcons|PlaceholderContentWidget)|utils[\\/]monacoSetup)|src[\\/](override[\\/])?composables[\\/]monaco[\\/]/
// Entries that look like typos are real unified-ecosystem package names.
const MARKDOWN_MODULES = /design-system[\\/]src[\\/]components[\\/]Data[\\/]KsMarkdown[\\/]|node_modules[\\/](shiki|@shikijs|oniguruma-to-es|regex(-recursion|-utilities)?|remark-[^\\/]+|micromark[^\\/]*|mdast[^\\/]*|unified|unist[^\\/]*|vfile[^\\/]*|hast[^\\/]*|devlop|ccount|character-[^\\/]+|decode-named-character-reference|markdown-table|longest-streak|trim-lines|zwitch|bail|trough|escape-string-regexp)[\\/]/ // codespell:ignore devlop,trough

const ECHARTS_MODULES = /node_modules[\\/](echarts|zrender|vue-echarts)[\\/]|design-system[\\/]src[\\/]components[\\/]Charts[\\/]/

/** Lazily loaded toolchains, each owning the private subtree of what it matches. */
const LAZY_GROUPS = [
    {name: "monaco", pattern: MONACO_MODULES},
    {name: "markdown", pattern: MARKDOWN_MODULES},
    {name: "echarts", pattern: ECHARTS_MODULES},
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
        const declared = new Set(ids.filter((id) => isRealModule(id) && pattern.test(id)))
        const subtree = new Set()
        for (const id of staticClosure(ctx, [...declared])) {
            if (eager.has(id) || declared.has(id) || !isRealModule(id)) continue
            if (others.some((group) => group.pattern.test(id))) continue
            subtree.add(id)
        }

        // Keep only what nothing outside the toolchain imports. A module the app
        // also uses would drag this whole chunk into every page importing it —
        // that is how a type-only helper like openFlow.ts once put Monaco on the
        // flow overview.
        for (let shrinking = true; shrinking;) {
            shrinking = false
            for (const id of subtree) {
                const importers = ctx.getModuleInfo(id)?.importers ?? []
                if (!importers.some((importer) => !declared.has(importer) && !subtree.has(importer))) continue
                subtree.delete(id)
                shrinking = true
            }
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

/**
 * The shared singletons' own implementations: everything the share shims
 * statically reach that no other group may claim. Filled during buildEnd.
 *
 * Unclaimed they get a chunk each beside the shim that is their only importer —
 * vue and the SDK alone are ~360 kB over two eagerly preloaded files. Limiting
 * the set to what {@link isRealModule} rejects is what makes the merge safe:
 * nothing is taken out of another group, so an outside importer can only add an
 * edge into mf-shared, never one back out of it.
 * @type {Set<string>}
 */
const sharedImpls = new Set()

/**
 * @param {import("rolldown").PluginContext} ctx
 */
function collectSharedImpls(ctx) {
    sharedImpls.clear()
    for (const id of staticClosure(ctx, [...ctx.getModuleIds()].filter(isShareShim))) {
        // The whole subtree, including modules the app also imports directly
        // (@vue/shared): one left behind lands in a chunk that imports
        // mf-shared while vue's own runtime in there imports it back.
        if (isMfInternal(id) || isRealModule(id)) continue
        sharedImpls.add(id)
    }
}

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

/**
 * Pages hot enough to deserve a chunk of their own, so that opening one costs a
 * couple of requests and moving between its tabs costs none.
 */
const PAGE_GROUPS = [
    {name: "login", pattern: /src[\\/]components[\\/](login[\\/]|basicauth[\\/]BasicAuthLogin)/},
    {name: "flow", pattern: /src[\\/](override[\\/])?components[\\/](flows|no-code|inputs)[\\/]/},
    {name: "execution", pattern: /src[\\/](override[\\/])?components[\\/](executions|logs)[\\/]/},
    {name: "dashboard", pattern: /src[\\/](dashboard[\\/]|(override[\\/])?components[\\/]dashboard[\\/])/},
    // Planned so its chunk carries the "-echarts" suffix: the page imports
    // echarts/core directly, and an unplanned chunk reaching a lazy toolchain
    // makes every page that reaches it download the toolchain.
    {name: "dependencies", pattern: /src[\\/](override[\\/])?components[\\/]dependencies[\\/]/},
]

/** {@link PAGE_GROUPS} widened with the patterns the build passed in. */
let pageGroups = PAGE_GROUPS

/** Chunk for what a lazy toolchain shares with the application. */
const TOOLCHAIN_SHARED = "toolchain-shared"

/** Under this many source bytes a chunk is not worth a request of its own. */
const MIN_CHUNK_SIZE = 320 * 1024

/**
 * Module id -> name of the chunk it belongs to, for everything the groups above
 * leave behind. Filled during buildEnd.
 * @type {Map<string, string>}
 */
const asyncChunk = new Map()

/**
 * Names the lazy toolchains a module statically reaches, so that a chunk holding
 * it is never one an unrelated page loads: that page would download Monaco too.
 * @param {import("rolldown").PluginContext} ctx
 * @param {string[]} ids
 * @returns {Map<string, string>}
 */
function collectLazyReach(ctx, ids) {
    /** @type {Map<string, Set<string>>} */
    const reach = new Map()
    for (const {name, pattern} of LAZY_GROUPS) {
        const queue = [...(lazySubtrees.get(name) ?? []), ...ids.filter((id) => pattern.test(id))]
        const seen = new Set(queue)
        while (queue.length) {
            const id = queue.pop()
            for (const importer of ctx.getModuleInfo(id)?.importers ?? []) {
                if (seen.has(importer)) continue
                seen.add(importer)
                queue.push(importer)
                const names = reach.get(importer)
                if (names) names.add(name)
                else reach.set(importer, new Set([name]))
            }
        }
    }
    return new Map([...reach].map(([id, names]) => [id, [...names].sort().join("-")]))
}

/** `pages` is the set of hot pages reaching the module, `lazy` the toolchains it reaches. */
const chunkName = (pages, lazy) => [pages.length > 1 ? `common-${pages.join("-")}` : pages[0], lazy].filter(Boolean).join("-")

/**
 * Plans a chunk for every module the groups above leave behind: one per hot page,
 * one per set of pages sharing code, one per feature directory for the rest.
 * @param {import("rolldown").PluginContext} ctx
 */
function planAsyncChunks(ctx) {
    asyncChunk.clear()
    const ids = [...ctx.getModuleIds()].filter(isRealModule)

    /** @type {Map<string, string[]>} */
    const pages = new Map()
    for (const {name, pattern} of pageGroups) {
        for (const id of staticClosure(ctx, ids.filter((candidate) => pattern.test(candidate)))) {
            if (!isRealModule(id)) continue
            const names = pages.get(id)
            if (names) names.push(name)
            else pages.set(id, [name])
        }
    }

    const reach = collectLazyReach(ctx, ids)
    // A module a toolchain imports gets a chunk of its own, so the toolchain
    // never has to reach into a chunk holding unrelated pages.
    const toolchain = new Set(LAZY_GROUPS.flatMap(({name, pattern}) =>
        [...(lazySubtrees.get(name) ?? []), ...ids.filter((id) => pattern.test(id))]))
    const usedByToolchain = new Set([...toolchain].flatMap((id) => ctx.getModuleInfo(id)?.importedIds ?? [])
        .filter((id) => !toolchain.has(id)))

    /** @type {Map<string, {size: number, pages: string[], lazy: string}>} */
    const chunks = new Map()
    for (const id of ids) {
        const owners = pages.get(id)?.sort() ?? []
        const lazy = reach.get(id) ?? ""
        // Only hot pages and the toolchains' shared modules are planned. Putting
        // what is left in one catch-all chunk was tried and doubled what a cold
        // page downloads (cases: 2.0 -> 4.7 MB gzip); automatic chunking splits
        // it finely, and a page nobody profiled fetches around a dozen files.
        const name = owners.length ? chunkName(owners, lazy)
            : usedByToolchain.has(id) ? chunkName([TOOLCHAIN_SHARED], lazy)
                : undefined
        if (!name) continue
        asyncChunk.set(id, name)
        const chunk = chunks.get(name) ?? {size: 0, pages: owners, lazy}
        chunk.size += ctx.getModuleInfo(id)?.code?.length ?? 0
        chunks.set(name, chunk)
    }

    // Fold the slivers into a chunk serving a superset of their pages: no page
    // gains a request, and only pages already loading the target gain bytes.
    /** @type {Map<string, string>} */
    const folded = new Map()
    for (const [name, chunk] of [...chunks].sort((a, b) => a[1].pages.length - b[1].pages.length)) {
        if (chunk.size >= MIN_CHUNK_SIZE || !chunk.pages.length) continue
        const superset = [...chunks]
            .filter(([other, candidate]) => other !== name && candidate.lazy === chunk.lazy &&
                candidate.pages.length > chunk.pages.length &&
                chunk.pages.every((page) => candidate.pages.includes(page)))
            .sort((a, b) => a[1].pages.length - b[1].pages.length)[0]
        if (superset) folded.set(name, superset[0])
    }
    const resolve = (name) => {
        const seen = new Set()
        let target = name
        while (folded.has(target) && !seen.has(target)) {
            seen.add(target)
            target = /** @type {string} */ (folded.get(target))
        }
        return target
    }
    for (const [id, name] of asyncChunk) asyncChunk.set(id, resolve(name))
    mergeCycles(ctx)
}

/**
 * Merges every group of planned chunks that import each other into one chunk.
 * Two chunks importing each other build cleanly and then throw ("X is not a
 * function") the first time either loads, so a cycle is never shippable.
 * @param {import("rolldown").PluginContext} ctx
 */
function mergeCycles(ctx) {
    /** @type {Map<string, Set<string>>} */
    const edges = new Map()
    for (const [id, name] of asyncChunk) {
        const targets = edges.get(name) ?? new Set()
        for (const dep of ctx.getModuleInfo(id)?.importedIds ?? []) {
            const target = asyncChunk.get(dep)
            if (target && target !== name) targets.add(target)
        }
        edges.set(name, targets)
    }

    // Tarjan: every component with more than one chunk is a cycle to collapse.
    let index = 0
    const order = new Map(), low = new Map(), onStack = new Set(), stack = []
    /** @type {Map<string, string>} */
    const merged = new Map()
    const visit = (name) => {
        order.set(name, index)
        low.set(name, index)
        index++
        stack.push(name)
        onStack.add(name)
        for (const target of edges.get(name) ?? []) {
            if (!order.has(target)) {
                visit(target)
                low.set(name, Math.min(low.get(name), low.get(target)))
            } else if (onStack.has(target)) {
                low.set(name, Math.min(low.get(name), order.get(target)))
            }
        }
        if (low.get(name) !== order.get(name)) return
        const component = []
        let popped
        do {
            popped = stack.pop()
            onStack.delete(popped)
            component.push(popped)
        } while (popped !== name)
        if (component.length === 1) return
        const target = component.sort()[0]
        for (const member of component) merged.set(member, target)
    }
    for (const name of edges.keys()) if (!order.has(name)) visit(name)

    for (const [id, name] of asyncChunk) {
        const target = merged.get(name)
        if (target) asyncChunk.set(id, target)
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
    // ECharts and the design-system chart components that statically import it,
    // in one lazy chunk (the charts are exported as async components). Claimed
    // before the design-system group, which would otherwise take the Charts
    // directory and pull ECharts back into the eager bundle with it.
    {
        name: "echarts",
        test: matchesWithLazySubtree("echarts", ECHARTS_MODULES),
        priority: -18,
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
    // One chunk for every module-federation share shim — alone they are
    // forty-odd two-kilobyte requests, most of them on every page — plus the
    // shared implementations behind them, which are on every page too.
    {
        name: "mf-shared",
        test: (id) => isShareShim(id) || sharedImpls.has(id),
        priority: -44,
        includeDependenciesRecursively: false,
    },
    // Everything reached only through a dynamic import, planned per page by
    // {@link planAsyncChunks} instead of left to fragment into hundreds of files.
    {
        name: (id) => asyncChunk.get(id) ?? null,
        priority: -50,
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
 * @param {{pages?: Record<string, RegExp>}} [options] Directories to count towards
 * a page, keyed by {@link PAGE_GROUPS} name — e.g. the tabs an edition adds to it.
 * @returns {import("vite").Plugin}
 */
export function consolidateChunks({pages = {}} = {}) {
    pageGroups = PAGE_GROUPS.map((page) => pages[page.name]
        ? {...page, pattern: new RegExp(`${page.pattern.source}|${pages[page.name].source}`)}
        : page)
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
            collectSharedImpls(this)
            planAsyncChunks(this)
        },
        generateBundle(_options, bundle) {
            // Static edges that undo the split: a page chunk reaching the
            // catch-all, or an untainted chunk reaching a lazy toolchain.
            const lazyNames = LAZY_GROUPS.map((group) => group.name)
            for (const chunk of Object.values(bundle)) {
                // shiki-langs belongs to the markdown toolchain and may reach it.
                if (chunk.type !== "chunk" || chunk.name === "shiki-langs" || lazyNames.includes(chunk.name)) continue
                for (const dependency of (chunk.imports ?? []).map((file) => bundle[file]?.name ?? "")) {
                    if (lazyNames.includes(dependency) && !chunk.name.includes(dependency)) {
                        this.error(`Chunk '${chunk.name}' statically imports the lazy '${dependency}' chunk, so every page reaching '${chunk.name}' now downloads it. Its modules should be reported as reaching '${dependency}' by collectLazyReach.`)
                    }
                }
            }

            // A cycle among the planned chunks is what mergeCycles exists to
            // prevent; one left standing throws at run time, not at build time.
            const planned = new Set(asyncChunk.values())
            const nameOf = (file) => bundle[file]?.name ?? ""
            const deps = (file) => (bundle[file]?.type === "chunk" ? bundle[file].imports ?? [] : [])
            for (const file of Object.keys(bundle)) {
                if (!planned.has(nameOf(file))) continue
                const stack = [[file]]
                const seen = new Set([file])
                while (stack.length) {
                    const path = /** @type {string[]} */ (stack.pop())
                    for (const dep of deps(path[path.length - 1])) {
                        if (dep === file) {
                            this.error(`Chunks import each other: ${[...path, dep].map(nameOf).join(" -> ")}. Either mergeCycles missed this cycle or a group outside the plan is part of it.`)
                        }
                        if (seen.has(dep) || !planned.has(nameOf(dep))) continue
                        seen.add(dep)
                        stack.push([...path, dep])
                    }
                }
            }

            // Folding the shared implementations in with their shims is only safe
            // while nothing mf-shared imports imports it back.
            const mfShared = Object.keys(bundle).find((file) => nameOf(file) === "mf-shared")
            if (mfShared) {
                const reached = new Set([mfShared])
                const paths = deps(mfShared).map((file) => [file])
                while (paths.length) {
                    const path = /** @type {string[]} */ (paths.pop())
                    const file = path[path.length - 1]
                    if (file === mfShared) {
                        this.error(`Chunks import each other: ${[mfShared, ...path].map(nameOf).join(" -> ")}. The shared singletons throw the first time any of these chunks loads; an implementation collectSharedImpls claimed reaches a chunk that consumes shares.`)
                    }
                    if (reached.has(file)) continue
                    reached.add(file)
                    paths.push(...deps(file).map((dep) => [...path, dep]))
                }
            }

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
