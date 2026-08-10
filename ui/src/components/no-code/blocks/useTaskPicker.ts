import {computed, nextTick, onBeforeUnmount, ref, watch, type Component, type ComponentPublicInstance, type Ref} from "vue"
import SuggestedIcon from "vue-material-design-icons/Creation.vue"
import AppsIcon from "vue-material-design-icons/ViewGridOutline.vue"
import RecentIcon from "vue-material-design-icons/History.vue"
import {
    addBlock,
    addBlockAtPath,
    buildMinimalTask,
    collectAllIds,
    isWrapperLane,
    wrapAsDagTask,
    type BlockSection,
} from "../../../utils/flowableBlockOps"
import {
    PICKER_MAX_RESULTS,
    SUGGESTED_FQCNS_BY_SECTION,
    buildPickerEntries,
    filterPickerEntries,
    groupPickerEntriesByApp,
    loadRecentFqcns,
    pushRecentFqcn,
    type PickerEntry,
} from "./taskPickerCatalog"
import {parentPathFromLaneSentinel, sectionFromParentPath, sectionFromSentinel} from "./blockSections"
import {computePickerPosition, type AnchorRect} from "./taskPickerPosition"

export type PickerTab = "suggested" | "apps" | "recent"

const SEARCH_DEBOUNCE = 150

interface PluginsStoreLike {
    plugins?: Record<string, unknown>[]
    ensurePlugins: () => Promise<unknown>
}

export interface TaskPickerDeps {
    pluginsStore: PluginsStoreLike
    editorEl: Ref<HTMLElement | undefined>
    focusedId: Ref<string | undefined>
    focusedAnchor: () => HTMLElement | undefined
    focusedBlockPath: () => string | undefined
    focusCanvasCard: (id: string | undefined) => void
    sectionList: (section: BlockSection) => Record<string, unknown>[]
    sectionDisplayLabel: (section: BlockSection) => string
    flowYaml: Ref<string>
    applyYaml: (yaml: string) => void
}

export function useTaskPicker(deps: TaskPickerDeps) {
    const taskPickerVisible = ref(false)
    const pickerAnchor = ref<HTMLElement>()
    const pickerSearchInput = ref<Element | ComponentPublicInstance | null>(null)
    const taskPickerSearch = ref("")
    const debouncedSearch = ref("")
    let searchTimer: ReturnType<typeof setTimeout> | undefined

    watch(taskPickerSearch, (value) => {
        clearTimeout(searchTimer)
        searchTimer = setTimeout(() => {
            debouncedSearch.value = value
        }, SEARCH_DEBOUNCE)
    })

    const hasSearch = computed(() => debouncedSearch.value.trim().length > 0)
    const taskPickerSection = ref<BlockSection>("tasks")
    const taskPickerParentPath = ref<string | undefined>(undefined)
    const taskPickerAfterIndex = ref<number | undefined>(undefined)
    const taskPickerPosition = ref<"before" | "after">("after")
    const pluginsLoading = ref(false)
    const pickerFocusedIndex = ref(-1)
    const pickerTab = ref<PickerTab>("suggested")
    const appFilter = ref<string | undefined>(undefined)
    const recentFqcns = ref<string[]>([])

    const PICKER_TABS: ReadonlyArray<{id: PickerTab; labelKey: string; icon: Component}> = [
        {id: "suggested", labelKey: "block_editor.tab_suggested", icon: SuggestedIcon},
        {id: "apps", labelKey: "block_editor.tab_apps", icon: AppsIcon},
        {id: "recent", labelKey: "block_editor.tab_recent", icon: RecentIcon},
    ]

    const activeEntryKind = computed(() => taskPickerSection.value === "triggers" ? "triggers" : "tasks")

    const allPickerEntries = computed<PickerEntry[]>(() =>
        buildPickerEntries(deps.pluginsStore.plugins, activeEntryKind.value),
    )

    const filteredMatches = computed<PickerEntry[]>(() =>
        filterPickerEntries(allPickerEntries.value, debouncedSearch.value),
    )

    const filteredCommonTypes = computed<PickerEntry[]>(() =>
        filteredMatches.value.slice(0, PICKER_MAX_RESULTS),
    )

    const pickerHiddenCount = computed(() =>
        Math.max(0, filteredMatches.value.length - PICKER_MAX_RESULTS),
    )

    const entryByFqcn = computed(() => {
        const map = new Map<string, PickerEntry>()
        for (const entry of allPickerEntries.value) map.set(entry.fqcn, entry)
        return map
    })

    const suggestedEntries = computed<PickerEntry[]>(() =>
        SUGGESTED_FQCNS_BY_SECTION[taskPickerSection.value]
            .map(fqcn => entryByFqcn.value.get(fqcn))
            .filter((e): e is PickerEntry => Boolean(e)),
    )

    const recentEntries = computed<PickerEntry[]>(() =>
        recentFqcns.value.map(fqcn => entryByFqcn.value.get(fqcn)).filter((e): e is PickerEntry => Boolean(e)),
    )

    const appGroups = computed(() => groupPickerEntriesByApp(allPickerEntries.value))

    const displayedEntries = computed<PickerEntry[]>(() => {
        if (hasSearch.value) return filteredCommonTypes.value
        if (pickerTab.value === "suggested") return suggestedEntries.value
        if (pickerTab.value === "recent") return recentEntries.value
        if (pickerTab.value === "apps" && appFilter.value) {
            return allPickerEntries.value.filter(e => e.group === appFilter.value).slice(0, PICKER_MAX_RESULTS)
        }
        return []
    })

    const sectionLabel = computed(() => deps.sectionDisplayLabel(taskPickerSection.value))

    function setPickerTab(tab: PickerTab) {
        pickerTab.value = tab
        appFilter.value = undefined
        pickerFocusedIndex.value = displayedEntries.value.length > 0 ? 0 : -1
    }

    watch(displayedEntries, (items) => {
        pickerFocusedIndex.value = items.length > 0 ? 0 : -1
    })

    function ensurePluginData() {
        if (deps.pluginsStore.plugins) return
        pluginsLoading.value = true
        deps.pluginsStore.ensurePlugins().finally(() => {
            pluginsLoading.value = false
        })
    }

    function anchorFrom(evt?: Event, explicitEl?: HTMLElement) {
        pickerAnchor.value = explicitEl ?? (evt?.currentTarget as HTMLElement) ?? deps.focusedAnchor() ?? deps.editorEl.value ?? undefined
        syncAnchorRect()
    }

    function resetPickerView() {
        taskPickerSearch.value = ""
        clearTimeout(searchTimer)
        debouncedSearch.value = ""
        pickerTab.value = "suggested"
        appFilter.value = undefined
        recentFqcns.value = loadRecentFqcns()
        pickerFocusedIndex.value = displayedEntries.value.length > 0 ? 0 : -1
    }

    function focusPickerSearch() {
        nextTick(() => (pickerSearchInput.value as {focus?: () => void} | null)?.focus?.())
    }

    function openTaskPicker(section: BlockSection, evt?: Event, anchorEl?: HTMLElement) {
        anchorFrom(evt, anchorEl)
        taskPickerSection.value = section
        taskPickerParentPath.value = undefined
        taskPickerAfterIndex.value = undefined
        taskPickerPosition.value = "after"
        resetPickerView()
        taskPickerVisible.value = true
        ensurePluginData()
        focusPickerSearch()
    }

    function openTaskPickerAtPath(
        parentPath: string,
        refIndex: number,
        evt?: Event,
        position: "before" | "after" = "after",
        anchorEl?: HTMLElement,
    ) {
        anchorFrom(evt, anchorEl)
        taskPickerSection.value = sectionFromParentPath(parentPath)
        taskPickerParentPath.value = parentPath
        taskPickerAfterIndex.value = refIndex >= 0 ? refIndex : undefined
        taskPickerPosition.value = position
        resetPickerView()
        taskPickerVisible.value = true
        ensurePluginData()
        focusPickerSearch()
    }

    function openTaskPickerAtTasksEnd() {
        const endDrop = deps.editorEl.value?.querySelector<HTMLElement>("[data-test='block-editor-tasks-end']") ?? undefined
        endDrop?.scrollIntoView({block: "nearest"})
        openTaskPicker("tasks", undefined, endDrop)
    }

    function openTaskPickerForSection(section: BlockSection) {
        const anchor = deps.editorEl.value?.querySelector<HTMLElement>(`[data-test='block-editor-section-head-${section}']`) ?? undefined
        anchor?.scrollIntoView({block: "nearest"})
        openTaskPicker(section, undefined, anchor)
    }

    function openTaskPickerRelativeToFocused(position: "before" | "after") {
        const sentinelSection = sectionFromSentinel(deps.focusedId.value)
        if (sentinelSection) {
            openTaskPicker(sentinelSection)
            return
        }
        const laneParentPath = parentPathFromLaneSentinel(deps.focusedId.value)
        if (laneParentPath) {
            openTaskPickerAtPath(laneParentPath, -1)
            return
        }
        const path = deps.focusedBlockPath()
        const match = path?.match(/^(.*)\[(\d+)\]$/)
        if (!match) {
            openTaskPickerAtTasksEnd()
            return
        }
        openTaskPickerAtPath(match[1], parseInt(match[2], 10), undefined, position)
    }

    function focusedInsertSection(): BlockSection {
        const sentinelSection = sectionFromSentinel(deps.focusedId.value)
        if (sentinelSection) return sentinelSection
        const laneParentPath = parentPathFromLaneSentinel(deps.focusedId.value)
        if (laneParentPath) return sectionFromParentPath(laneParentPath)
        const path = deps.focusedBlockPath()
        const match = path?.match(/^(.*)\[(\d+)\]$/)
        return match ? sectionFromParentPath(match[1]) : "tasks"
    }

    const focusedContextEntries = computed<PickerEntry[]>(() =>
        buildPickerEntries(deps.pluginsStore.plugins, focusedInsertSection() === "triggers" ? "triggers" : "tasks"),
    )

    // Insertion needs the picker's own target state, so reuse it instead of duplicating the resolution rules.
    function insertTaskInFocusedContext(fqcn: string) {
        openTaskPickerRelativeToFocused("after")
        insertTask(fqcn)
    }

    const anchorRect = ref<AnchorRect | null>(null)

    function syncAnchorRect() {
        const anchor = pickerAnchor.value
        if (!anchor) {
            anchorRect.value = null
            return
        }
        const {top, bottom, left} = anchor.getBoundingClientRect()
        anchorRect.value = {top, bottom, left}
    }

    function trackAnchor() {
        window.addEventListener("scroll", syncAnchorRect, {capture: true, passive: true})
        window.addEventListener("resize", syncAnchorRect, {passive: true})
    }

    function untrackAnchor() {
        window.removeEventListener("scroll", syncAnchorRect, {capture: true})
        window.removeEventListener("resize", syncAnchorRect)
    }

    watch(taskPickerVisible, (visible) => {
        if (!visible) {
            untrackAnchor()
            return
        }
        syncAnchorRect()
        trackAnchor()
        nextTick(() => requestAnimationFrame(syncAnchorRect))
    })

    onBeforeUnmount(untrackAnchor)

    const pickerStyle = computed(() =>
        anchorRect.value
            ? computePickerPosition(anchorRect.value, {width: window.innerWidth, height: window.innerHeight})
            : {},
    )

    function insertTask(fqcn: string) {
        recentFqcns.value = pushRecentFqcn(fqcn, recentFqcns.value)
        const block = buildMinimalTask(fqcn, collectAllIds(deps.flowYaml.value))

        if (taskPickerParentPath.value !== undefined) {
            const parentPath = taskPickerParentPath.value
            const blockToInsert = isWrapperLane(deps.flowYaml.value, parentPath) ? wrapAsDagTask(block) : block
            deps.applyYaml(addBlockAtPath(deps.flowYaml.value, parentPath, blockToInsert, taskPickerAfterIndex.value, taskPickerPosition.value))
        } else {
            const section = taskPickerSection.value
            const list = deps.sectionList(section)
            const lastId = list.length > 0
                ? String(list[list.length - 1].id ?? "")
                : undefined
            deps.applyYaml(addBlock(deps.flowYaml.value, section, block, lastId))
        }
        deps.focusCanvasCard(String(block.id))
        taskPickerVisible.value = false
    }

    function onPickerKeydown(event: KeyboardEvent) {
        const list = displayedEntries.value
        if (list.length === 0) return
        if (event.key === "ArrowDown") {
            event.preventDefault()
            pickerFocusedIndex.value = Math.min(pickerFocusedIndex.value + 1, list.length - 1)
        } else if (event.key === "ArrowUp") {
            event.preventDefault()
            pickerFocusedIndex.value = Math.max(pickerFocusedIndex.value - 1, 0)
        } else if (event.key === "Enter" && pickerFocusedIndex.value >= 0) {
            event.preventDefault()
            const entry = list[pickerFocusedIndex.value]
            if (entry) insertTask(entry.fqcn)
        }
    }

    return {
        taskPickerVisible,
        pickerSearchInput,
        taskPickerSearch,
        hasSearch,
        taskPickerSection,
        taskPickerParentPath,
        pluginsLoading,
        pickerFocusedIndex,
        pickerTab,
        appFilter,
        PICKER_TABS,
        allPickerEntries,
        filteredCommonTypes,
        pickerHiddenCount,
        appGroups,
        displayedEntries,
        sectionLabel,
        pickerStyle,
        focusedContextEntries,
        ensurePluginData,
        setPickerTab,
        insertTask,
        insertTaskInFocusedContext,
        onPickerKeydown,
        openTaskPicker,
        openTaskPickerAtPath,
        openTaskPickerAtTasksEnd,
        openTaskPickerForSection,
        openTaskPickerRelativeToFocused,
    }
}

export type TaskPickerApi = ReturnType<typeof useTaskPicker>
