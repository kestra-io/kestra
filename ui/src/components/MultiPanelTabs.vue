<template>
    <KsSplitter class="default-theme" v-bind="$attrs" @resize-end="onResize">
        <div v-if="!panels.length" class="empty-panels">
            <KsNoData
                :icon="ViewArrayOutline"
                :title="$t('empty.panels.title')"
                :description="$t('empty.panels.content')"
            />
        </div>
        <template v-else>
            <KsSplitterPanel
                v-for="{panel, panelIndex} in renderedPanels"
                min="10%"
                :key="`${panelIndex}:${maximizedPanelIndex === panelIndex}`"
                :size="panelSizes[panelIndex] ?? panel.size"
                @dragover.prevent="(e:DragEvent) => panelDragOver(e, panelIndex)"
                @dragleave.prevent="panelDragLeave"
                @drop.prevent="(e:DragEvent) => panelDrop(e, panelIndex)"
                :class="{'panel-dragover': panel.dragover, 'panel-maximized': maximizedPanelIndex === panelIndex}"
            >
                <template v-if="maximizedPanelIndex === panelIndex">
                    <button
                        v-if="leftNeighbor?.activeTab"
                        type="button"
                        class="maximized-sliver maximized-sliver--left"
                        :title="$t('multi_panel_editor.exit_fullscreen')"
                        :aria-label="$t('multi_panel_editor.exit_fullscreen')"
                        data-test="maximized-sliver-left"
                        @click="toggleMaximize(panelIndex)"
                    >
                        <component :is="leftNeighbor.activeTab.button.icon" class="maximized-sliver-icon" />
                        <span class="maximized-sliver-label">{{ leftNeighbor.activeTab.button.label }}</span>
                    </button>
                    <button
                        v-if="rightNeighbor?.activeTab"
                        type="button"
                        class="maximized-sliver maximized-sliver--right"
                        :title="$t('multi_panel_editor.exit_fullscreen')"
                        :aria-label="$t('multi_panel_editor.exit_fullscreen')"
                        data-test="maximized-sliver-right"
                        @click="toggleMaximize(panelIndex)"
                    >
                        <component :is="rightNeighbor.activeTab.button.icon" class="maximized-sliver-icon" />
                        <span class="maximized-sliver-label">{{ rightNeighbor.activeTab.button.label }}</span>
                    </button>
                </template>
                <div class="editor-tabs-container">
                    <KsButton
                        :icon="DotsGrid"
                        link
                        class="tab-icon drag-handle"
                        draggable="true"
                        @dragstart="(e:DragEvent) => panelDragStart(e, panelIndex)"
                    />
                    <div
                        class="editor-tabs"
                        role="tablist"
                        @dragover.prevent="dragover"
                        @dragleave.prevent="throttle(removeAllPotentialTabs, 300)"
                        @drop="drop"
                        @wheel.passive="onWheelTabScroll"
                        :data-panel-index="panelIndex"
                        :class="{dragover: panel.dragover}"
                        ref="tabContainerRefs"
                    >
                        <template
                            v-for="tab in panel.tabs"
                            :key="tab.uid"
                        >
                            <button
                                v-if="!tab.potential"
                                class="editor-tab"
                                role="tab"
                                :class="{active: tab.uid === panel.activeTab?.uid}"
                                draggable="true"
                                @dragstart="(e) => {
                                    if(e.dataTransfer){
                                        e.dataTransfer.effectAllowed = 'move';
                                    }
                                    dragstart(panelIndex, tab.uid);
                                }"
                                @dragleave.prevent
                                :data-tab-id="tab.uid"
                                @click="handleTabClick(panelIndex, panel, tab)"
                                @mouseup="middleMouseClose($event, panelIndex, tab)"
                            >
                                <component :is="tab.button.icon" class="tab-icon" />
                                <span class="tab-title">{{ tab.button.label }}</span>
                                <CircleMediumIcon v-if="tab.dirty" class="dirty-icon" />
                                <CloseIcon
                                    @click.stop="destroyTab(panelIndex, tab)"
                                    class="tab-icon close-icon"
                                    :title="$t('close')"
                                />
                            </button>
                            <div v-else class="potential-container">
                                <div class="potential" />
                            </div>
                        </template>
                    </div>
                    <div class="buttons-container">
                        <button
                            type="button"
                            class="maximize_panel"
                            :title="maximizedPanelIndex === panelIndex ? $t('multi_panel_editor.exit_fullscreen') : $t('multi_panel_editor.fullscreen')"
                            :aria-label="maximizedPanelIndex === panelIndex ? $t('multi_panel_editor.exit_fullscreen') : $t('multi_panel_editor.fullscreen')"
                            :aria-pressed="maximizedPanelIndex === panelIndex"
                            data-test="panel-maximize"
                            @click="toggleMaximize(panelIndex)"
                        >
                            <FullscreenExit v-if="maximizedPanelIndex === panelIndex" />
                            <Fullscreen v-else />
                        </button>
                        <button
                            v-if="panel.tabs.filter(t => !t.potential).length > 1"
                            @click="splitPanel(panelIndex)"
                            class="split_right"
                            title="Split panel"
                        >
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path
                                    fill-rule="evenodd"
                                    clip-rule="evenodd"
                                    d="M22.038 20.5599C22.0402 21.35 21.4014 21.9924 20.6112 21.9946L3.47196 22.0424C2.6818 22.0446 2.03946 21.4058 2.03725 20.6157L1.98939 3.45824C1.98718 2.66808 2.62595 2.02574 3.41611 2.02353L20.5554 1.97571C21.3455 1.97351 21.9879 2.61228 21.9901 3.40244L22.038 20.5599ZM20.626 20.5807L10.5998 20.6086L10.5517 3.37297L20.5779 3.345L20.626 20.5807ZM9.10343 20.611L3.38734 20.6269L3.33925 3.39126L9.05535 3.37531L9.10343 20.611Z"
                                    fill="currentColor"
                                />
                            </svg>
                        </button>

                        <KsDropdown trigger="click" placement="bottom-end">
                            <KsButton :icon="DotsVertical" link class="me-2 tab-icon" :aria-label="$t('panel actions')" />
                            <template #dropdown>
                                <KsDropdownMenu>
                                    <KsDropdownItem
                                        :icon="DockRight"
                                        :disabled="panelIndex === panels.length - 1"
                                        @click="movePanel(panelIndex, 'right')"
                                    >
                                        <span class="small-text">
                                            {{ $t("multi_panel_editor.move_right") }}
                                        </span>
                                    </KsDropdownItem>
                                    <KsDropdownItem
                                        :icon="DockLeft"
                                        :disabled="panelIndex === 0"
                                        @click="movePanel(panelIndex, 'left')"
                                    >
                                        <span class="small-text">
                                            {{ $t("multi_panel_editor.move_left") }}
                                        </span>
                                    </KsDropdownItem>
                                    <KsDropdownItem v-if="panel.tabs.length > 1" :icon="Close" @click="closeAllTabs(panelIndex)">
                                        <span class="small-text">
                                            {{ $t("multi_panel_editor.close_all_tabs") }}
                                        </span>
                                    </KsDropdownItem>
                                    <KsDropdownItem :icon="Close" @click="closeAllPanels()">
                                        <span class="small-text">
                                            {{ $t("multi_panel_editor.close_all_panels") }}
                                        </span>
                                    </KsDropdownItem>
                                    <KsDropdownItem
                                        v-if="panel.activeTab?.uid === 'code'"
                                        :icon="Keyboard"
                                        @click="showKeyShortcuts()"
                                    >
                                        <span class="small-text">
                                            {{ $t("editor_shortcuts.label") }}
                                        </span>
                                    </KsDropdownItem>
                                </KsDropdownMenu>
                            </template>
                        </KsDropdown>
                    </div>
                </div>
                <div
                    class="content-panel"
                    :data-panel-index="panelIndex"
                    @drop="drop"
                    @dragover.prevent="dragover"
                    @dragleave.prevent="removeAllPotentialTabs"
                    @dragenter.prevent
                >
                    <KeepAlive v-if="panel.activeTab" :include="accessibleTabsKeys">
                        <component
                            :key="panel.activeTab.uid"
                            :is="createUniqueComponent(panel.activeTab.component, panel.activeTab.uid)"
                            :panelIndex="panelIndex"
                            :tabIndex="panel.tabs.findIndex(t => t.uid === panel.activeTab.uid)"
                        />
                    </KeepAlive>
                    <div
                        v-if="dragging"
                        class="editor-content-overlay"
                        :class="{dragover: panel.dragover}"
                    />
                </div>
            </KsSplitterPanel>
        </template>
    </KsSplitter>

    <div
        v-if="showDropZones"
        class="absolute-drop-zones-container"
    >
        <div
            class="new-panel-drop-zone left-drop-zone"
            :class="{'panel-dragover': leftPanelDragover}"
            @dragover.prevent="leftPanelDragOver"
            @dragleave.prevent="leftPanelDragLeave"
            @drop.prevent="(e) => newPanelDrop(e, 'left')"
        />

        <div
            class="new-panel-drop-zone right-drop-zone"
            :class="{'panel-dragover': rightPanelDragover}"
            @dragover.prevent="rightPanelDragOver"
            @dragleave.prevent="rightPanelDragLeave"
            @drop.prevent="(e) => newPanelDrop(e, 'right')"
        />
    </div>
</template>

<script setup lang="ts">
    import {nextTick, ref, watch, provide, computed, defineComponent, h, markRaw, onMounted, onBeforeUnmount} from "vue"

    import {VISIBLE_PANELS_INJECTION_KEY, PANEL_MAXIMIZED_INJECTION_KEY} from "./no-code/injectionKeys"
    import {useKeyShortcuts} from "../utils/useKeyShortcuts"

    import CloseIcon from "vue-material-design-icons/Close.vue"
    import CircleMediumIcon from "vue-material-design-icons/CircleMedium.vue"
    import DotsGrid from "vue-material-design-icons/DotsGrid.vue"
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue"
    import DockLeft from "vue-material-design-icons/DockLeft.vue"
    import DockRight from "vue-material-design-icons/DockRight.vue"
    import Close from "vue-material-design-icons/Close.vue"
    import Keyboard from "vue-material-design-icons/Keyboard.vue"
    import ViewArrayOutline from "vue-material-design-icons/ViewArrayOutline.vue"
    import Fullscreen from "vue-material-design-icons/Fullscreen.vue"
    import FullscreenExit from "vue-material-design-icons/FullscreenExit.vue"

    import {trackTabOpen, trackTabClose} from "../utils/tabTracking"
    import {Panel, Tab, TabLive} from "../utils/multiPanelTypes"

    const {showKeyShortcuts} = useKeyShortcuts()

    function throttle(callback: () => void, limit: number): () => void {
        let waiting = false
        return function () {
            if (!waiting) {
                callback()
                waiting = true
                setTimeout(function () {
                    waiting = false
                }, limit)
            }
        }
    }

    const ComponentCache = new Map<string, any>()

    const createUniqueComponent = (component: any, key: string) => {
        if(ComponentCache.has(key)){
            return ComponentCache.get(key)
        }
        const uniqueComponent = markRaw(
            defineComponent({
                name: makeNoCodeComponentName(key),
                inheritAttrs: true,
                render() {
                    return h(component)
                },
            }),
        )
        ComponentCache.set(key, uniqueComponent)
        return uniqueComponent
    }

    function makeNoCodeComponentName(key: string){
        return `KsNoCode-${key}`
    }

    const accessibleTabsKeys = computed<string[]>(() => {
        return panels.value.flatMap(panel => panel.tabs.map(tab => makeNoCodeComponentName(tab.uid)))
    })

    interface TabInfo {
        panelIndex: number,
        tabId: string,
        tabIndex: number,
        tab: TabLive
    }

    const panels = defineModel<Panel<TabLive>[]>({
        required: true,
    })

    provide(VISIBLE_PANELS_INJECTION_KEY, panels)

    const emit = defineEmits<{
        removeTab: [tab: string]
    }>()

    const maximizedPanelIndex = ref<number | null>(null)

    provide(PANEL_MAXIMIZED_INJECTION_KEY, computed(() => maximizedPanelIndex.value !== null))

    const renderedPanels = computed(() => {
        const index = maximizedPanelIndex.value
        if (index != null && panels.value[index]) {
            return [{panel: panels.value[index], panelIndex: index}]
        }
        return panels.value.map((panel, panelIndex) => ({panel, panelIndex}))
    })

    const leftNeighbor = computed(() => {
        const index = maximizedPanelIndex.value
        return index != null && index > 0 ? panels.value[index - 1] : null
    })

    const rightNeighbor = computed(() => {
        const index = maximizedPanelIndex.value
        return index != null && index < panels.value.length - 1 ? panels.value[index + 1] : null
    })

    function toggleMaximize(panelIndex: number) {
        maximizedPanelIndex.value = maximizedPanelIndex.value === panelIndex ? null : panelIndex
    }

    watch(() => panels.value.length, (length) => {
        if (maximizedPanelIndex.value != null && maximizedPanelIndex.value >= length) {
            maximizedPanelIndex.value = null
        }
    })

    function onFullscreenKeydown(event: KeyboardEvent) {
        if (event.key !== "Escape" || maximizedPanelIndex.value == null) return
        const target = event.target as HTMLElement | null
        if (target && (target.closest(".monaco-editor") || ["INPUT", "TEXTAREA"].includes(target.tagName) || target.isContentEditable)) return
        maximizedPanelIndex.value = null
    }

    onMounted(() => window.addEventListener("keydown", onFullscreenKeydown))
    onBeforeUnmount(() => window.removeEventListener("keydown", onFullscreenKeydown))

    const mouseXRef = ref(-1)
    const movedTabInfo = ref<TabInfo | null>(null)
    const dragging = ref(false)
    const tabContainerRefs = ref<HTMLDivElement[]>([])
    const draggingPanel = ref<number | null>(null)
    const realDragging = ref(false)
    const leftPanelDragover = ref(false)
    const rightPanelDragover = ref(false)

    const handleTabClick = (panelIndex: number, panel: Panel, tab: Tab) => {
        trackTabOpen(tab)

        panel.activeTab = tab

        nextTick(() => ensureActiveTabVisible(panelIndex, tab.uid))
    }

    const showDropZones = computed(() =>
        realDragging.value &&
        movedTabInfo.value &&
        !draggingPanel.value,
    )

    function onResize(_index: number, sizes: number[]) {
        const sumSizes = sizes.reduce((a, b) => a + b, 0) / 100

        // Element Plus resize event provides sizes array and index of the resized panel
        for (let i = 0; i < panels.value.length && i < sizes.length; i++) {
            panels.value[i].size = sizes[i] / sumSizes
        }
    }

    // let the panelSizes be dealt with by the KsSplitter once set
    // by the prop
    const panelSizes = computed<number[]>((prevValue) => {
        if(prevValue?.length === panels.value.length){
            return prevValue
        }
        return panels.value.map(panel => panel.size)
    })

    function dragstart(panelIndex: number, tabId: string) {
        dragging.value = true
        const tabIndex = panels.value[panelIndex].tabs.findIndex((tab) => tab.uid === tabId)
        movedTabInfo.value = {panelIndex, tabId, tabIndex, tab: panels.value[panelIndex].tabs[tabIndex]}
    }

    function cleanUp(){
        dragging.value = false
        realDragging.value = false
        mouseXRef.value = -1
        leftPanelDragover.value = false
        rightPanelDragover.value = false
        nextTick(() => {
            movedTabInfo.value = null
            for(const panel of panels.value) {
                panel.dragover = false
                panel.tabs = panel.tabs.filter((tab) => !tab.potential)
            }
        })
    }

    function getPanelIndex(e: DragEvent): number {
        const target = e.currentTarget as HTMLElement
        return parseInt(target.dataset.panelIndex ?? "-1")
    }

    function removeAllPotentialTabs(){
        for(const panel of panels.value){
            panel.tabs = panel.tabs.filter((tab) => !tab.potential)
        }
    }

    function dragover(e: DragEvent) {
        // Ensure we set the realDragging flag when a drag operation is in progress
        if (movedTabInfo.value) {
            realDragging.value = true
            dragging.value = true
        }

        // if mouse has not moved vertically, stop the processing
        // this will be triggered every few ms so perf and readability will be paramount
        if(mouseXRef.value === e.clientX){
            return
        }

        mouseXRef.value = e.clientX

        if(!movedTabInfo.value){
            return
        }

        const panelIndex = getPanelIndex(e)
        if(panelIndex === -1) {
            return
        }

        const activePanel = tabContainerRefs.value.find((r) => r.dataset.panelIndex === panelIndex.toString())
        const tabsInPanel = Array.from(activePanel?.querySelectorAll(".editor-tab") || []) as HTMLElement[]

        let insertTabAfterIndex = -1
        let i = 0
        const mouseX = e.clientX
        for(const tab of tabsInPanel){
            const br = tab.getBoundingClientRect()
            // get the X position of the middle of the tab
            const middle = br.left + br.width / 2
            // if we are beyond the middle of the last tab
            if(mouseX > middle && i === tabsInPanel.length - 1){
                insertTabAfterIndex = i
                break
            } else
                // if we are before the middle of the first tab
                if(mouseX < middle && i === 0){
                    insertTabAfterIndex = i - 1
                    break
                }else
                    // figure out if we should insert the tab between the current and the next tab
                    if(mouseX > middle && tabsInPanel[i + 1]){
                        const nextBr = tabsInPanel[i + 1].getBoundingClientRect()
                        const middleNext = nextBr.left + nextBr.width / 2
                        if(mouseX < middleNext){
                            insertTabAfterIndex = i
                            break
                        }
                    }
            i++
        }

        // if the potential tab is already inserted in the right place
        if(panels.value[panelIndex].tabs[insertTabAfterIndex + 1]?.potential){
            return
        }

        removeAllPotentialTabs()

        // then insert the potential tab in the right place
        panels.value[panelIndex].tabs.splice(insertTabAfterIndex + 1, 0, {
            ...movedTabInfo.value.tab,
            uid: `potential-${movedTabInfo.value.tab.uid}`,
            potential: true,
            fromPanel: panelIndex === movedTabInfo.value.panelIndex,
        })
    }

    function getTargetTabIndex(targetPanelIndex: number, targetTabId?: string): number {
        const targetTabIndex = panels.value[targetPanelIndex].tabs.findIndex((tab) => tab.uid === targetTabId)
        if(targetTabIndex === -1){
            return panels.value[targetPanelIndex].tabs.length
        }
        return targetTabIndex
    }

    function drop(){
        if(!movedTabInfo.value){
            return
        }

        // find potential tab in panels.value tabs
        const potentialTabPanelIndex = panels.value.findIndex((panel) => panel.tabs.some((tab) => tab.potential))
        const potentialTabId = panels.value[potentialTabPanelIndex]?.tabs.find((tab) => tab.potential)?.uid

        if(potentialTabId){
            moveTab(movedTabInfo.value, potentialTabPanelIndex, potentialTabId)
        }

        cleanUp()
    }

    function moveTab(movedTabInfoOpt: TabInfo, targetPanelIndex: number, targetTabId?: string){
        const {tab: movedTab, panelIndex: originalPanelIndex, tabIndex} = movedTabInfoOpt

        const targetTabIndex = getTargetTabIndex(targetPanelIndex, targetTabId)

        // In case of reordering of tabs we have to
        // account for cases where potential tabs are present.
        // They will take a slot in the list
        if(targetPanelIndex === originalPanelIndex){
            if (targetTabIndex === tabIndex || panels.value[targetPanelIndex].tabs.length <= 1) {
                return
            }

            if (targetTabIndex < tabIndex){
                panels.value[originalPanelIndex].tabs.splice(tabIndex + 1, 1)
            } else {
                panels.value[originalPanelIndex].tabs.splice(tabIndex, 1)
            }
        } else {
            // remove the tab from the original panel
            panels.value[originalPanelIndex].tabs.splice(tabIndex, 1)

            // if the tab has been removed from the panel
            // we need to select another active tab
            if(panels.value[originalPanelIndex].activeTab.uid === movedTab.uid){
                // if the tab at the same index is available, select it
                if(tabIndex >= 0 && panels.value[originalPanelIndex].tabs.length > tabIndex){
                    panels.value[originalPanelIndex].activeTab = panels.value[originalPanelIndex].tabs[tabIndex]
                } else
                    // if it would fall out of bounds, use the previous tab
                    // NOTE: no worries if it is null, it will select null instead
                    if(tabIndex === panels.value[originalPanelIndex].tabs.length){
                        panels.value[originalPanelIndex].activeTab = panels.value[originalPanelIndex].tabs[tabIndex - 1]
                    }
            }
        }

        if(targetPanelIndex === originalPanelIndex){
            // if moving tabs on the same panel, add the tab to the target panel in-place of the hovered potential tab
            const insertIndex = targetTabIndex < tabIndex ? targetTabIndex + 1 : targetTabIndex
            panels.value[targetPanelIndex].tabs.splice(insertIndex, 0, movedTab)
        } else {
            // add the tab to the target panel in-place of the hovered potential tab
            panels.value[targetPanelIndex].tabs.splice(targetTabIndex + 1, 0, movedTab)
        }
    }

    const defaultSize = computed(() => panels.value.length === 0 ? 1 : (panels.value.reduce((acc, panel) => acc + panel.size, 0) / panels.value.length))

    function newPanelDrop(_e: DragEvent, direction: "left" | "right") {
        if (!movedTabInfo.value) return

        const {tab: movedTab} = movedTabInfo.value

        // Create a new panel with the dragged tab
        const newPanel = {
            tabs: [movedTab],
            activeTab: movedTab,
            size: defaultSize.value,
        }

        // Add the new panel based on the drop direction, not relative to original panel
        if (direction === "left") {
            panels.value.splice(0, 0, newPanel)
        } else {
            panels.value.push(newPanel)
        }

        // Remove the tab from the original panel
        // After adding the new panel, the original panel's index may have changed
        // Find it again by looking for the tab in all panels
        for (let i = 0; i < panels.value.length; i++) {
            const panel = panels.value[i]
            const tabIndex = panel.tabs.findIndex(t => t.uid === movedTab.uid)

            if (i === 0 && direction === "left") continue
            if (i === panels.value.length - 1 && direction === "right") continue

            if (tabIndex !== -1) {
                panel.tabs.splice(tabIndex, 1)

                if (panel.activeTab.uid === movedTab.uid && panel.tabs.length > 0) {
                    panel.activeTab = tabIndex > 0
                        ? panel.tabs[tabIndex - 1]
                        : panel.tabs[0]
                }
                break
            }
        }

        cleanUp()
    }

    function closeAllTabs(panelIndex: number){
        const panel = panels.value[panelIndex]
        panel.tabs.forEach(tab => {
            trackTabClose(tab)
        })

        panels.value[panelIndex].tabs = []
    }

    function closeAllPanels(){
        panels.value = []
    }

    function destroyTab(panelIndex:number, tab: Tab){
        trackTabClose(tab)

        const panel = panels.value[panelIndex]
        const tabIndex = panel.tabs.findIndex((t) => t.uid === tab.uid)
        panel.tabs.splice(tabIndex, 1)
        if (panel.activeTab.uid === tab.uid) {
            panel.activeTab = panel.tabs[tabIndex - 1] ?? panel.tabs[0]
        }
        emit("removeTab", tab.uid)
    }

    watch(panels, () => {
        for (let index = panels.value.length - 1; index >= 0; index--) {
            if (panels.value[index].tabs.length === 0) {
                panels.value.splice(index, 1)
                if (maximizedPanelIndex.value === index) {
                    maximizedPanelIndex.value = null
                } else if (maximizedPanelIndex.value != null && index < maximizedPanelIndex.value) {
                    maximizedPanelIndex.value--
                }
            }
        }
    }, {deep: true})

    function splitPanel(panelIndex: number){
        const panel = panels.value[panelIndex]
        const newPanel = {
            tabs: [panel.activeTab],
            activeTab: panel.activeTab,
            size: defaultSize.value,
        }
        panels.value.splice(panelIndex + 1, 0, newPanel)

        // get index of active tab in the original panel
        const activeTabIndex = panel.tabs.findIndex((tab) => tab.uid === panel.activeTab.uid)

        // set the active tab to the previous tab in the original panel
        panel.activeTab = panel.tabs[activeTabIndex - 1] ?? panel.tabs[activeTabIndex + 1]

        // remove the tab from the original panel
        panel.tabs.splice(activeTabIndex, 1)
    }

    function panelDragStart(e: DragEvent, panelIndex: number) {
        if (e.dataTransfer) {
            e.dataTransfer.effectAllowed = "move"
            draggingPanel.value = panelIndex
        }
    }

    function panelDragOver(_e: DragEvent, panelIndex: number) {
        if (draggingPanel.value === null || draggingPanel.value === panelIndex) return

        panels.value.forEach(panel => panel.dragover = false)
        panels.value[panelIndex].dragover = true
    }

    function panelDragLeave() {
        panels.value.forEach(panel => panel.dragover = false)
    }

    function panelDrop(_e: DragEvent, targetPanelIndex: number) {
        if (draggingPanel.value === null || draggingPanel.value === targetPanelIndex) return

        const panelsCopy = [...panels.value]
        const [movedPanel] = panelsCopy.splice(draggingPanel.value, 1)
        panelsCopy.splice(targetPanelIndex, 0, movedPanel)

        panels.value = panelsCopy

        draggingPanel.value = null
        panelDragLeave()
    }

    function movePanel(panelIndex: number, direction: "left" | "right") {
        const newIndex = direction === "left" ? panelIndex - 1 : panelIndex + 1
        if (newIndex < 0 || newIndex >= panels.value.length) return

        const panelsCopy = [...panels.value]
        const [movedPanel] = panelsCopy.splice(panelIndex, 1)
        panelsCopy.splice(newIndex, 0, movedPanel)
        panels.value = panelsCopy
    }

    function rightPanelDragOver() {
        if (!movedTabInfo.value) return
        rightPanelDragover.value = true
        leftPanelDragover.value = false
        removeAllPotentialTabs()
    }

    function rightPanelDragLeave() {
        rightPanelDragover.value = false
    }

    function leftPanelDragOver() {
        if (!movedTabInfo.value) return
        leftPanelDragover.value = true
        rightPanelDragover.value = false
        removeAllPotentialTabs()
    }

    function leftPanelDragLeave() {
        leftPanelDragover.value = false
    }

    function middleMouseClose(event:MouseEvent, panelIndex:number, tab: Tab) {
        // Middle mouse button
        if (event.button === 1) {
            event.preventDefault()
            destroyTab(panelIndex, tab)
        }
    }

    function onWheelTabScroll(e: WheelEvent){
        // Make vertical wheel scroll the tab list horizontally (VS Code behavior)
        const el = e.currentTarget as HTMLElement
        if(!el){
            return
        }

        const overflows = el.scrollWidth > el.clientWidth
        if(!overflows){
            return
        }

        const delta = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY
        el.scrollLeft += delta
    }

    function ensureActiveTabVisible(panelIndex: number, tabId: string){
        const container = tabContainerRefs.value[panelIndex]
        if(!container){
            return
        }
        const safeId = (globalThis as any).CSS?.escape ? (globalThis as any).CSS.escape(tabId) : tabId.replace(/[^a-zA-Z0-9_-]/g, "\\$&")
        const el = container.querySelector(`.editor-tab[data-tab-id="${safeId}"]`) as HTMLElement | null
        if(!el){
            return
        }
        const left = el.offsetLeft
        const right = left + el.offsetWidth
        const cLeft = container.scrollLeft
        const cRight = cLeft + container.clientWidth

        if (left < cLeft){
            container.scrollLeft = left - 16 // small padding
        } else if (right > cRight){
            container.scrollLeft = right - container.clientWidth + 16
        }
    }
</script>

<style scoped lang="scss">
    .panel-maximized {
        position: relative;
        background: var(--ks-bg-base);
    }

    .maximized-sliver {
        position: absolute;
        top: var(--ks-spacing-5);
        bottom: var(--ks-spacing-5);
        z-index: 2;
        width: 2vw;
        min-width: 22px;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3) 0;
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        color: var(--ks-icon-muted);
        cursor: pointer;
        overflow: hidden;
        transition: background 0.15s ease, color 0.15s ease, width 0.15s ease;
    }

    .maximized-sliver--left {
        left: 0;
        border-left: none;
        border-top-right-radius: var(--ks-radius-base);
        border-bottom-right-radius: var(--ks-radius-base);
    }

    .maximized-sliver--right {
        right: 0;
        border-right: none;
        border-top-left-radius: var(--ks-radius-base);
        border-bottom-left-radius: var(--ks-radius-base);
    }

    .maximized-sliver:hover {
        width: calc(2vw + var(--ks-spacing-3));
        background: var(--ks-bg-hover-elevated);
        color: var(--ks-text-primary);
    }

    .maximized-sliver-icon {
        flex-shrink: 0;
        font-size: var(--ks-font-size-md);
        line-height: 1;
    }

    .maximized-sliver-label {
        writing-mode: vertical-rl;
        font-size: var(--ks-font-size-xs);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        max-height: 70%;
    }

    .panel-maximized .editor-tabs-container,
    .panel-maximized .content-panel {
        position: relative;
        z-index: 1;
        height: calc(100% - var(--ks-spacing-5));
        margin-left: calc(2vw + var(--ks-spacing-4));
        margin-right: calc(2vw + var(--ks-spacing-4));
        background: var(--ks-bg-surface);
        border-left: 1px solid var(--ks-border-default);
        border-right: 1px solid var(--ks-border-default);
        box-shadow: var(--ks-shadow-md);
    }

    .panel-maximized .editor-tabs-container {
        margin-top: var(--ks-spacing-5);
        border-top: 1px solid var(--ks-border-default);
        border-top-left-radius: var(--ks-radius-base);
        border-top-right-radius: var(--ks-radius-base);
    }

    .panel-maximized .content-panel {
        border-bottom: 1px solid var(--ks-border-default);
        border-bottom-left-radius: var(--ks-radius-base);
        border-bottom-right-radius: var(--ks-radius-base);
    }

    .editor-tabs-container{
        display: grid;
        grid-template-columns: auto 1fr auto;
        background-color: var(--ks-bg-base);
        border-bottom: 1px solid var(--ks-border-default);
        align-items: center;
        padding-top: var(--ks-spacing-2);
        gap: var(--ks-spacing-1);

        button.split_right,
        button.maximize_panel{
            border: none;
            color: var(--ks-text-dim);
            background-color: transparent;
            padding: 0 var(--ks-spacing-2);
            line-height: 16px;
            cursor: pointer;
            svg {
                height: 16px;
                width: 16px;
            }
            &:hover {
                color: var(--ks-text-primary);
            }
        }
        .buttons-container{
            display: flex;

        }
        .drag-handle {
            cursor: grab;
            opacity: 0.7;
            padding: var(--ks-spacing-2);
            &:hover {
                opacity: 1;
            }
            &:active {
                cursor: grabbing;
            }
        }
    }

    .editor-content-overlay{
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        background-color: rgba(0, 0, 0, 0.1);
        z-index: 100;
        &.dragover{
            background-color: rgba(0, 0, 0, 0.3);
        }
    }

    .editor-tabs {
        display: flex;
        flex: 1;
        align-items: end;
        padding-bottom: 0;
        font-size: var(--ks-font-size-xs);
        line-height: 1.5rem;
        overflow-x: auto;
        overflow-y: hidden;
        scrollbar-width: none;
        gap: var(--ks-spacing-1);
        &.dragover {
            background-color: var(--ks-bg-hover-elevated);
        }
    }

    .tab-icon{
        color: var(--ks-icon-muted);
    }

    .small-text {
        font-size: var(--ks-font-size-xs);
    }

    .editor-tabs .editor-tab{
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border: none;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-lg) var(--ks-radius-lg) 0 0;
        border-bottom: none;
        background-color: var(--ks-btn-secondary-bg-default);
        display: flex;
        flex-wrap: nowrap;
        /* Prevent shrinking so tabs overflow and the container can scroll */
        flex: 0 0 auto;
        min-width: 120px;
        max-width: 240px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        align-items: center;
        gap: var(--ks-spacing-2);
        color: var(--ks-text-primary);
        opacity: .5;

        &.active {
            opacity: 1;
        }

        .tab-title{
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            flex: 1 1 auto;
        }

        .dirty-icon{
            font-size: var(--ks-font-size-base);
            flex: 0 0 auto;
        }

        .close-icon{
            flex: 0 0 auto;
            opacity: .6;
            cursor: pointer;
            color: var(--ks-icon-default);

            &:hover{
                opacity: 1;
            }
        }
    }

    .editor-tabs::-webkit-scrollbar {
        height: 6px;
    }
    .editor-tabs::-webkit-scrollbar-track {
        background: transparent;
    }
    .editor-tabs::-webkit-scrollbar-thumb {
        background-color: var(--ks-border-default);
        border-radius: 3px;
    }

    .potential-container{
        position: relative;
        height: 100%;
        pointer-events: none;
    }
    .potential{
        z-index: 1;
        position: absolute;
        opacity: .6;
        left: -.5px;
        bottom: 0;
        border-radius: 2px 2px 0 0;
        width: 4px;
        transform: translateX(-50%);
        height: 85%;
        background-color: var(--ks-text-primary);
        pointer-events: none;
    }

    .default-theme{
        :deep(.kel-splitter-panel) {
            background-color: var(--ks-bg-surface);
            display: grid;
            grid-template-rows: auto 1fr;
        }

        :deep(.kel-splitter__splitter){
            border-left-color: var(--ks-border-default);
            background-color: var(--ks-bg-surface);
            &:before, &:after{
                background-color: var(--ks-text-secondary);
            }
        }

        :deep(.kel-splitter-bar) {
            z-index: 0;
        }
    }

    .content-panel{
        position: relative;
        height: 100%;
        overflow: auto;
    }

    .empty-panels {
        flex: 1;
        display: flex;
        justify-content: center;
    }

    .kel-splitter-panel{
        transition: none;
        &.dragging {
            opacity: 0.5;
            background-color: var(--ks-bg-hover-elevated);
            transition: opacity 0.2s ease;
        }
    }

    .panel-dragover {
        background-color: var(--ks-bg-hover-elevated);
        transition: background-color 0.2s ease;
    }

    .absolute-drop-zones-container {
        position: absolute;
        top: 0;
        left: 0;
        right: 0;
        bottom: 0;
        pointer-events: none;
        z-index: 100;
        display: flex;
        justify-content: space-between;
    }

    .new-panel-drop-zone {
        position: relative;
        width: 60px;
        display: flex;
        align-items: center;
        justify-content: center;
        background-color: rgba(30, 30, 30, 0.5);
        transition: all 0.2s ease;
        border: 2px dashed var(--ks-border-default, #444);
        border-radius: 4px;
        margin: 8px;
        pointer-events: auto;
        height: calc(100% - 16px);
    }

    .new-panel-drop-zone:hover,
    .new-panel-drop-zone.panel-dragover {
        background-color: rgba(40, 40, 40, 0.8);
        border-color: var(--ks-border-focus, #888);
    }

    .left-drop-zone {
        border-right-width: 2px;
    }

    .right-drop-zone {
        border-left-width: 2px;
    }

</style>
