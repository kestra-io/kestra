<template>
    <el-splitter class="default-theme" v-bind="$attrs" @resize-end="onResize">
        <Empty v-if="!panels.length" type="panels" />
        <template v-else>
            <el-splitter-panel
                v-for="(panel, panelIndex) in panels"
                min="10%"
                :key="panelIndex"
                :size="panelSizes[panelIndex] ?? panel.size"
                @dragover.prevent="(e:DragEvent) => panelDragOver(e, panelIndex)"
                @dragleave.prevent="panelDragLeave"
                @drop.prevent="(e:DragEvent) => panelDrop(e, panelIndex)"
                :class="{'panel-dragover': panel.dragover}"
            >
                <div class="editor-tabs-container">
                    <el-button
                        :icon="DragVertical"
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

                        <el-dropdown trigger="click" placement="bottom-end">
                            <el-button :icon="DotsVertical" link class="me-2 tab-icon" />
                            <template #dropdown>
                                <el-dropdown-menu class="m-2">
                                    <el-dropdown-item
                                        :icon="DockRight"
                                        :disabled="panelIndex === panels.length - 1"
                                        @click="movePanel(panelIndex, 'right')"
                                    >
                                        <span class="small-text">
                                            {{ $t("multi_panel_editor.move_right") }}
                                        </span>
                                    </el-dropdown-item>
                                    <el-dropdown-item
                                        :icon="DockLeft"
                                        :disabled="panelIndex === 0"
                                        @click="movePanel(panelIndex, 'left')"
                                    >
                                        <span class="small-text">
                                            {{ $t("multi_panel_editor.move_left") }}
                                        </span>
                                    </el-dropdown-item>
                                    <el-dropdown-item v-if="panel.tabs.length > 1" :icon="Close" @click="closeAllTabs(panelIndex)">
                                        <span class="small-text">
                                            {{ $t("multi_panel_editor.close_all_tabs") }}
                                        </span>
                                    </el-dropdown-item>
                                    <el-dropdown-item :icon="Close" @click="closeAllPanels()">
                                        <span class="small-text">
                                            {{ $t("multi_panel_editor.close_all_panels") }}
                                        </span>
                                    </el-dropdown-item>
                                    <el-dropdown-item
                                        v-if="panel.activeTab?.uid === 'code'"
                                        :icon="Keyboard"
                                        @click="showKeyShortcuts()"
                                    >
                                        <span class="small-text">
                                            {{ $t("editor_shortcuts.label") }}
                                        </span>
                                    </el-dropdown-item>
                                </el-dropdown-menu>
                            </template>
                        </el-dropdown>
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
                    <KeepAlive v-if="panel.activeTab">
                        <component
                            :key="panel.activeTab.uid"
                            :is="panel.activeTab.component"
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
            </el-splitter-panel>
        </template>
    </el-splitter>

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
    import {nextTick, ref, watch, provide, computed} from "vue";

    import {VISIBLE_PANELS_INJECTION_KEY} from "./no-code/injectionKeys";
    import {useKeyShortcuts} from "../utils/useKeyShortcuts";

    import Empty from "./layout/empty/Empty.vue";

    import CloseIcon from "vue-material-design-icons/Close.vue"
    import CircleMediumIcon from "vue-material-design-icons/CircleMedium.vue"
    import DragVertical from "vue-material-design-icons/DragVertical.vue";
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
    import DockLeft from "vue-material-design-icons/DockLeft.vue";
    import DockRight from "vue-material-design-icons/DockRight.vue";
    import Close from "vue-material-design-icons/Close.vue";
    import Keyboard from "vue-material-design-icons/Keyboard.vue";

    import {trackTabOpen, trackTabClose} from "../utils/tabTracking";
    import {Panel, Tab, TabLive} from "../utils/multiPanelTypes";

    const {showKeyShortcuts} = useKeyShortcuts();

    function throttle(callback: () => void, limit: number): () => void {
        let waiting = false;
        return function () {
            if (!waiting) {
                callback();
                waiting = true;
                setTimeout(function () {
                    waiting = false;
                }, limit);
            }
        }
    }

    interface TabInfo {
        panelIndex: number,
        tabId: string,
        tabIndex: number,
        tab: TabLive
    }

    const panels = defineModel<Panel<TabLive>[]>({
        required: true,
    })

    provide(VISIBLE_PANELS_INJECTION_KEY, panels);

    const emit = defineEmits<{
        removeTab: [tab: string]
    }>()

    const mouseXRef = ref(-1);
    const movedTabInfo = ref<TabInfo | null>(null);
    const dragging = ref(false);
    const tabContainerRefs = ref<HTMLDivElement[]>([]);
    const draggingPanel = ref<number | null>(null);
    const realDragging = ref(false);
    const leftPanelDragover = ref(false);
    const rightPanelDragover = ref(false);

    const handleTabClick = (panelIndex: number, panel: Panel, tab: Tab) => {
        trackTabOpen(tab);

        panel.activeTab = tab

        nextTick(() => ensureActiveTabVisible(panelIndex, tab.uid));
    };

    const showDropZones = computed(() =>
        realDragging.value &&
        movedTabInfo.value &&
        !draggingPanel.value
    );

    function onResize(_index: number, sizes: number[]) {
        const sumSizes = sizes.reduce((a, b) => a + b, 0) / 100;

        // Element Plus resize event provides sizes array and index of the resized panel
        for (let i = 0; i < panels.value.length && i < sizes.length; i++) {
            panels.value[i].size = sizes[i] / sumSizes;
        }
    }

    // let the panelSizes be dealt with by the el-splitter once set
    // by the prop
    const panelSizes = computed<number[]>((prevValue) => {
        if(prevValue?.length === panels.value.length){
            return prevValue
        }
        return panels.value.map(panel => panel.size);
    });

    function dragstart(panelIndex: number, tabId: string) {
        dragging.value = true;
        const tabIndex = panels.value[panelIndex].tabs.findIndex((tab) => tab.uid === tabId);
        movedTabInfo.value = {panelIndex, tabId, tabIndex, tab: panels.value[panelIndex].tabs[tabIndex]}
    }

    function cleanUp(){
        dragging.value = false;
        realDragging.value = false;
        mouseXRef.value = -1;
        leftPanelDragover.value = false;
        rightPanelDragover.value = false;
        nextTick(() => {
            movedTabInfo.value = null
            for(const panel of panels.value) {
                panel.dragover = false;
                panel.tabs = panel.tabs.filter((tab) => !tab.potential)
            }
        })
    }

    function getPanelIndex(e: DragEvent): number {
        const target = e.currentTarget as HTMLElement;
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
            realDragging.value = true;
            dragging.value = true;
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

        const panelIndex = getPanelIndex(e);
        if(panelIndex === -1) {
            return
        }


        const activePanel = tabContainerRefs.value.find((ref) => ref.dataset.panelIndex === panelIndex.toString());
        const tabsInPanel = Array.from(activePanel?.querySelectorAll(".editor-tab") || []) as HTMLElement[];

        let insertTabAfterIndex = -1
        let i = 0;
        const mouseX = e.clientX
        for(const tab of tabsInPanel){
            const br = tab.getBoundingClientRect();
            // get the X position of the middle of the tab
            const middle = br.left + br.width / 2;
            // if we are beyond the middle of the last tab
            if(mouseX > middle && i === tabsInPanel.length - 1){
                insertTabAfterIndex = i;
                break;
            } else
                // if we are before the middle of the first tab
                if(mouseX < middle && i === 0){
                    insertTabAfterIndex = i - 1;
                    break;
                }else
                    // figure out if we should insert the tab between the current and the next tab
                    if(mouseX > middle && tabsInPanel[i + 1]){
                        const nextBr = tabsInPanel[i + 1].getBoundingClientRect();
                        const middleNext = nextBr.left + nextBr.width / 2;
                        if(mouseX < middleNext){
                            insertTabAfterIndex = i;
                            break;
                        }
                    }
            i++;
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
            fromPanel: panelIndex === movedTabInfo.value.panelIndex
        });
    }

    function getTargetTabIndex(targetPanelIndex: number, targetTabId?: string): number {
        const targetTabIndex = panels.value[targetPanelIndex].tabs.findIndex((tab) => tab.uid === targetTabId)
        if(targetTabIndex === -1){
            return panels.value[targetPanelIndex].tabs.length;
        }
        return targetTabIndex;
    }

    function drop(){
        if(!movedTabInfo.value){
            return
        }

        // find potential tab in panels.value tabs
        const potentialTabPanelIndex = panels.value.findIndex((panel) => panel.tabs.some((tab) => tab.potential));
        const potentialTabId = panels.value[potentialTabPanelIndex]?.tabs.find((tab) => tab.potential)?.uid;

        if(potentialTabId){
            moveTab(movedTabInfo.value, potentialTabPanelIndex, potentialTabId);
        }

        cleanUp();
    }

    function moveTab(movedTabInfo: TabInfo, targetPanelIndex: number, targetTabId?: string){
        const {tab: movedTab, panelIndex: originalPanelIndex, tabIndex} = movedTabInfo

        const targetTabIndex = getTargetTabIndex(targetPanelIndex, targetTabId);

        // In case of reordering of tabs we have to
        // account for cases where potential tabs are present.
        // They will take a slot in the list
        if(targetPanelIndex === originalPanelIndex){
            if (targetTabIndex === tabIndex || panels.value[targetPanelIndex].tabs.length <= 1) {
                return
            }

            if (targetTabIndex < tabIndex){
                panels.value[originalPanelIndex].tabs.splice(tabIndex + 1, 1);
            } else {
                panels.value[originalPanelIndex].tabs.splice(tabIndex, 1);
            }
        } else {
            // remove the tab from the original panel
            panels.value[originalPanelIndex].tabs.splice(tabIndex, 1);

            // if the tab has been removed from the panel
            // we need to select another active tab
            if(panels.value[originalPanelIndex].activeTab.uid === movedTab.uid){
                // if the tab at the same index is available, select it
                if(tabIndex >= 0 && panels.value[originalPanelIndex].tabs.length > tabIndex){
                    panels.value[originalPanelIndex].activeTab = panels.value[originalPanelIndex].tabs[tabIndex];
                } else
                    // if it would fall out of bounds, use the previous tab
                    // NOTE: no worries if it is null, it will select null instead
                    if(tabIndex === panels.value[originalPanelIndex].tabs.length){
                        panels.value[originalPanelIndex].activeTab = panels.value[originalPanelIndex].tabs[tabIndex - 1];
                    }
            }
        }

        if(targetPanelIndex === originalPanelIndex){
            // if moving tabs on the same panel, add the tab to the target panel in-place of the hovered potential tab
            const insertIndex = targetTabIndex < tabIndex ? targetTabIndex + 1 : targetTabIndex;
            panels.value[targetPanelIndex].tabs.splice(insertIndex, 0, movedTab);
        } else {
            // add the tab to the target panel in-place of the hovered potential tab
            panels.value[targetPanelIndex].tabs.splice(targetTabIndex + 1, 0, movedTab);
        }
    }

    const defaultSize = computed(() => panels.value.length === 0 ? 1 : (panels.value.reduce((acc, panel) => acc + panel.size, 0) / panels.value.length));

    function newPanelDrop(_e: DragEvent, direction: "left" | "right") {
        if (!movedTabInfo.value) return;

        const {tab: movedTab} = movedTabInfo.value;

        // Create a new panel with the dragged tab
        const newPanel = {
            tabs: [movedTab],
            activeTab: movedTab,
            size: defaultSize.value
        };

        // Add the new panel based on the drop direction, not relative to original panel
        if (direction === "left") {
            panels.value.splice(0, 0, newPanel);
        } else {
            panels.value.push(newPanel);
        }

        // Remove the tab from the original panel
        // After adding the new panel, the original panel's index may have changed
        // Find it again by looking for the tab in all panels
        for (let i = 0; i < panels.value.length; i++) {
            const panel = panels.value[i];
            const tabIndex = panel.tabs.findIndex(t => t.uid === movedTab.uid);

            if (i === 0 && direction === "left") continue;
            if (i === panels.value.length - 1 && direction === "right") continue;

            if (tabIndex !== -1) {
                panel.tabs.splice(tabIndex, 1);

                if (panel.activeTab.uid === movedTab.uid && panel.tabs.length > 0) {
                    panel.activeTab = tabIndex > 0
                        ? panel.tabs[tabIndex - 1]
                        : panel.tabs[0];
                }
                break;
            }
        }

        cleanUp();
    }

    function closeAllTabs(panelIndex: number){
        const panel = panels.value[panelIndex];
        panel.tabs.forEach(tab => {
            trackTabClose(tab);
        });

        panels.value[panelIndex].tabs = [];
    }

    function closeAllPanels(){
        panels.value = [];
    }

    function destroyTab(panelIndex:number, tab: Tab){
        trackTabClose(tab);

        const panel = panels.value[panelIndex];
        const tabIndex = panel.tabs.findIndex((t) => t.uid === tab.uid);
        panel.tabs.splice(tabIndex, 1);
        if (panel.activeTab.uid === tab.uid) {
            panel.activeTab = panel.tabs[tabIndex - 1] ?? panel.tabs[0];
        }
        emit("removeTab", tab.uid)
    }

    watch(panels, () => {
        let index = 0;
        for (const panel of panels.value) {
            if (panel.tabs.length === 0) {
                panels.value.splice(index, 1);
            }
            index++;
        }
    }, {deep: true})

    function splitPanel(panelIndex: number){
        const panel = panels.value[panelIndex];
        const newPanel = {
            tabs: [panel.activeTab],
            activeTab: panel.activeTab,
            size: defaultSize.value
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
            e.dataTransfer.effectAllowed = "move";
            draggingPanel.value = panelIndex;
        }
    }

    function panelDragOver(_e: DragEvent, panelIndex: number) {
        if (draggingPanel.value === null || draggingPanel.value === panelIndex) return;

        panels.value.forEach(panel => panel.dragover = false);
        panels.value[panelIndex].dragover = true;
    }

    function panelDragLeave() {
        panels.value.forEach(panel => panel.dragover = false);
    }

    function panelDrop(_e: DragEvent, targetPanelIndex: number) {
        if (draggingPanel.value === null || draggingPanel.value === targetPanelIndex) return;

        const panelsCopy = [...panels.value];
        const [movedPanel] = panelsCopy.splice(draggingPanel.value, 1);
        panelsCopy.splice(targetPanelIndex, 0, movedPanel);

        panels.value = panelsCopy;

        draggingPanel.value = null;
        panelDragLeave();
    }

    function movePanel(panelIndex: number, direction: "left" | "right") {
        const newIndex = direction === "left" ? panelIndex - 1 : panelIndex + 1;
        if (newIndex < 0 || newIndex >= panels.value.length) return;

        const panelsCopy = [...panels.value];
        const [movedPanel] = panelsCopy.splice(panelIndex, 1);
        panelsCopy.splice(newIndex, 0, movedPanel);
        panels.value = panelsCopy;
    }

    function rightPanelDragOver() {
        if (!movedTabInfo.value) return;
        rightPanelDragover.value = true;
        leftPanelDragover.value = false;
        removeAllPotentialTabs();
    }

    function rightPanelDragLeave() {
        rightPanelDragover.value = false;
    }

    function leftPanelDragOver() {
        if (!movedTabInfo.value) return;
        leftPanelDragover.value = true;
        rightPanelDragover.value = false;
        removeAllPotentialTabs();
    }

    function leftPanelDragLeave() {
        leftPanelDragover.value = false;
    }

    function middleMouseClose(event:MouseEvent, panelIndex:number, tab: Tab) {
        // Middle mouse button
        if (event.button === 1) {
            event.preventDefault();
            destroyTab(panelIndex, tab);
        }
    }

    function onWheelTabScroll(e: WheelEvent){
        // Make vertical wheel scroll the tab list horizontally (VS Code behavior)
        const el = e.currentTarget as HTMLElement;
        if(!el){
            return;
        }

        const overflows = el.scrollWidth > el.clientWidth;
        if(!overflows){
            return;
        }


        const delta = Math.abs(e.deltaX) > Math.abs(e.deltaY) ? e.deltaX : e.deltaY;
        el.scrollLeft += delta;
    }

    function ensureActiveTabVisible(panelIndex: number, tabId: string){
        const container = tabContainerRefs.value[panelIndex];
        if(!container){
            return;
        }
        const safeId = (globalThis as any).CSS?.escape ? (globalThis as any).CSS.escape(tabId) : tabId.replace(/[^a-zA-Z0-9_-]/g, "\\$&");
        const el = container.querySelector(`.editor-tab[data-tab-id="${safeId}"]`) as HTMLElement | null;
        if(!el){
            return;
        }
        const left = el.offsetLeft;
        const right = left + el.offsetWidth;
        const cLeft = container.scrollLeft;
        const cRight = cLeft + container.clientWidth;

        if (left < cLeft){
            container.scrollLeft = left - 16; // small padding
        } else if (right > cRight){
            container.scrollLeft = right - container.clientWidth + 16;
        }
    }
</script>

<style scoped lang="scss">
    .editor-tabs-container{
        display: grid;
        grid-template-columns: auto 1fr auto;
        background-color: var(--ks-background-body);
        border-bottom: 1px solid var(--ks-border-primary);
        align-items: center;

        button.split_right{
            border: none;
            color: var(--ks-content-tertiary);
            background-color: transparent;
            padding: 0 .5rem;
            line-height: 16px;
            svg {
                height: 16px;
                width: 16px;
            }
        }
        .buttons-container{
            display: flex;

        }
        .drag-handle {
            cursor: grab;
            opacity: 0.5;
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
        font-size: .8rem;
        border-left: 1px solid var(--ks-border-primary);
        line-height: 1.5rem;
        overflow-x: auto;
        overflow-y: hidden;
        scrollbar-width: none;
        &.dragover {
            background-color: var(--ks-background-card-hover);
        }
    }

    .tab-icon{
        color: var(--ks-content-inactive);
    }

    .small-text {
        font-size: .8rem;
    }

    :deep(.el-dropdown-menu__item.is-disabled) {
        color: var(--ks-border-inactive);
    }

    .editor-tabs .editor-tab{
        padding: 3px .5rem;
        border: none;
        border-right: 1px solid var(--ks-border-primary);
        border-radius: 2px 2px 0 0;
        border-bottom: none;
        background-color: var(--ks-background-card);
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
        gap: .5rem;
        color: var(--ks-content-secondary);
        opacity: .6;

        &.active {
            opacity: 1;
            color: var(--ks-content-primary);
        }

        .tab-title{
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            flex: 1 1 auto;
        }

        .dirty-icon{
            font-size: 16px;
            flex: 0 0 auto;
        }

        .close-icon{
            flex: 0 0 auto;
            opacity: .6;
            cursor: pointer;
        }
        &:hover .close-icon{
            opacity: 1;
        }
    }

    .editor-tabs::-webkit-scrollbar {
        height: 6px;
    }
    .editor-tabs::-webkit-scrollbar-track {
        background: transparent;
    }
    .editor-tabs::-webkit-scrollbar-thumb {
        background-color: var(--ks-border-primary);
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
        background-color: var(--ks-content-primary);
        pointer-events: none;
    }

    .default-theme{
        :deep(.el-splitter-panel) {
            background-color: var(--ks-background-panel);
            display: grid;
            grid-template-rows: auto 1fr;
        }

        :deep(.el-splitter__splitter){
            border-left-color: var(--ks-border-primary);
            background-color: var(--ks-background-panel);
            &:before, &:after{
                background-color: var(--ks-content-secondary);
            }
        }
    }

    .content-panel{
        position: relative;
        height: 100%;
        overflow: auto;
    }

    .el-splitter-panel{
        transition: none;
        &.dragging {
            opacity: 0.5;
            background-color: var(--ks-background-card-hover);
            transition: opacity 0.2s ease;
        }
    }

    .panel-dragover {
        background-color: var(--ks-background-card-hover);
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
        border: 2px dashed var(--ks-border-primary, #444);
        border-radius: 4px;
        margin: 8px;
        pointer-events: auto;
        height: calc(100% - 16px);
    }

    .new-panel-drop-zone:hover,
    .new-panel-drop-zone.panel-dragover {
        background-color: rgba(40, 40, 40, 0.8);
        border-color: var(--ks-border-active, #888);
    }

    .left-drop-zone {
        border-right-width: 2px;
    }

    .right-drop-zone {
        border-left-width: 2px;
    }


</style>
