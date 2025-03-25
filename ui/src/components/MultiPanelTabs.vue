<template>
    <Splitpanes class="default-theme" @resize="onResize">
        <Pane v-for="(panel, panelIndex) in panels" min-size="10" :key="panelIndex" :size="panel.size">
            <div class="editor-tabs-container">
                <div
                    class="editor-tabs"
                    role="tablist"
                    @dragleave.prevent="dragleavePanel"
                    @dragenter.prevent="dragoverPanel"
                    @dragover.prevent
                    @drop="drop"
                    :data-panel-index="panelIndex"
                    :class="{dragover: panel.dragover}"
                >
                    <template
                        v-for="tab in panel.tabs"
                        :key="tab.value"
                    >
                        <button
                            v-if="!tab.potential"
                            class="editor-tab"
                            role="tab"
                            :class="{active: tab.value === panel.activeTab?.value}"
                            draggable="true"
                            @dragstart="() => dragstart(panelIndex, tab.value)"
                            @dragend="cleanUp"
                            @dragenter.prevent.stop="dragover"
                            @dragover.prevent
                            @drop.stop="drop"
                            :data-tab-id="tab.value"
                            @click="panel.activeTab = tab"
                        >
                            <component :is="tab.button.icon" class="tab-icon" />
                            {{ tab.button.label }}
                            <CircleMediumIcon v-if="tab.dirty" class="dirty-icon" />
                            <CloseIcon @click.stop="destroyTab(panelIndex, tab)" class="tab-icon" />
                        </button>
                        <div
                            v-else
                            class="editor-tab simulated"
                            @drop.stop="drop"
                            :data-tab-id="tab.value"
                        >
                            <component
                                :is="tab.button.icon"
                                @dragover.prevent.stop
                                @dragleave.prevent.stop
                                @dragenter.prevent.stop
                            />
                            {{ tab.button.label }}
                        </div>
                    </template>
                </div>
                <button
                    v-if="panel.tabs.length > 1"
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
            </div>
            <div class="content-panel">
                <component :is="panel.activeTab?.component" />
                <div
                    v-if="dragging"
                    class="editor-content-overlay"
                    :class="{dragover: panel.dragover}"
                    @dragleave.prevent="dragleavePanel"
                    @dragenter.prevent="dragoverContent"
                    @dragover.prevent
                    @drop="drop"
                    :data-panel-index="panelIndex"
                />
            </div>
        </Pane>
    </Splitpanes>
</template>

<script lang="ts" setup>
    import {nextTick, ref, watch} from "vue";
    import "splitpanes/dist/splitpanes.css"
    import {Splitpanes, Pane} from "splitpanes"
    import CloseIcon from "vue-material-design-icons/Close.vue"
    import CircleMediumIcon from "vue-material-design-icons/CircleMedium.vue"

    export interface Tab {
        button: {
            icon: any,
            label: string
        },
        potential?: boolean
        fromPanel?: boolean
        value: string,
        dirty?: boolean,
        component: any
    }

    interface TabInfo {
        panelIndex: number,
        tabId: string,
        tabIndex: number,
        tab: Tab
    }

    export interface Panel {
        size?: number;
        tabs: Tab[],
        dragover?:boolean,
        activeTab: Tab,
    }

    const panels = defineModel<Panel[]>({
        required: true,
    })

    const emit = defineEmits<{
        removeTab: [tab: string]
    }>()

    const movedTabInfo = ref<TabInfo | null>(null);
    const dragging = ref(false);

    function onResize(e: {size:number}[]) {
        let i = 0;
        for(const p of panels.value){
            p.size = e[i++].size
        }
    }

    function dragstart(panelIndex: number, tabId: string) {
        dragging.value = true;
        const tabIndex = panels.value[panelIndex].tabs.findIndex((tab) => tab.value === tabId);
        movedTabInfo.value = {panelIndex, tabId, tabIndex, tab: panels.value[panelIndex].tabs[tabIndex]}
    }

    function cleanUp(){
        dragging.value = false;
        nextTick(() => {
            movedTabInfo.value = null
            for(const panel of panels.value) {
                panel.dragover = false;
                panel.tabs = panel.tabs.filter((tab) => !tab.potential)
            }
        })
    }

    function getPanelIndex(fromPanel: boolean, target: HTMLElement): number | undefined {
        let targetPanelIndex = 0;
        if (fromPanel) {
            targetPanelIndex = parseInt(target.getAttribute("data-panel-index") ?? "-1");
            if(targetPanelIndex < 0){
                return
            }
        } else {
            targetPanelIndex = parseInt(target.closest(".editor-tabs")?.getAttribute("data-panel-index") ?? "-1");
            if(targetPanelIndex < 0){
                return
            }
            const targetTabId = target.getAttribute("data-tab-id") ?? ""
            if(targetTabId === movedTabInfo.value?.tab?.value) {
                return
            }
        }

        return targetPanelIndex
    }

    const mousePosition = ref({clientX: 0, clientY: 0})

    function dragover(event: DragEvent, fromPanel: boolean = false) {
        if(!(event.target instanceof HTMLElement)){
            return
        }

        const {clientX, clientY} = event;

        mousePosition.value = {clientX, clientY}

        const targetPanelIndex = getPanelIndex(fromPanel, event.target);
        if(targetPanelIndex === undefined){
            return
        }

        // skip if the target is the same as the original panel
        if(fromPanel && movedTabInfo.value?.panelIndex === targetPanelIndex){
            return
        }
        if(fromPanel && panels.value[targetPanelIndex]){
            panels.value[targetPanelIndex].dragover = true;
        }

        const tabId = event.target.getAttribute("data-tab-id")

        const targetTabIndex = tabId
            ? panels.value[targetPanelIndex].tabs.findIndex((tab) => tab.value === tabId)
            : panels.value[targetPanelIndex].tabs.length;

        // avoid cloning the tab just beside itself
        if(!fromPanel
            && movedTabInfo.value?.panelIndex === targetPanelIndex
            && (movedTabInfo.value?.tabIndex === targetTabIndex)){
            return
        }

        const movedTabInfoVal = movedTabInfo.value;
        if(!movedTabInfoVal?.tab) {
            return;
        }

        // add a simulated tab to the target panel to see where the tab will be placed
        const tab = {
            component: () => null,
            value: "simulated-" + movedTabInfoVal.tab.value,
            fromPanel,
            potential:true,
            button: {
                ...movedTabInfoVal.tab.button,
            }
        }

        // avoid having multiple simulated tabs
        for(const p of panels.value){
            if(p.tabs.some(t => t.value === tab.value)){
                // remove any already present simulated tab
                p.tabs = p.tabs.filter((t) => !t.potential)
            }
        }

        panels.value[targetPanelIndex].tabs.splice(targetTabIndex, 0, tab);
    }


    function dragleave(event: DragEvent, fromPanel: boolean = false) {
        if(!(event.target instanceof HTMLElement)) {
            return
        }

        // is the mouse has not moved at all between over and leave events
        // the leave is due to the tab disappearing when it should not. Let's
        // recreate it instead
        if(event.clientX === mousePosition.value.clientX && event.clientY === mousePosition.value.clientY){
            dragover(event)
            return
        }

        let targetPanelIndex = getPanelIndex(fromPanel, event.target)
        if(targetPanelIndex === undefined){
            return
        }
        panels.value[targetPanelIndex].dragover = false;

        // remove the simulated tab from the target panel
        panels.value[targetPanelIndex].tabs = panels.value[targetPanelIndex].tabs.filter((tab) => !tab.potential)
    }

    const panelTimeout = ref<any>(null);

    function dragoverPanel(event: DragEvent) {
        panelTimeout.value = setTimeout(() => {
            dragover(event, true);
        }, 50)
    }

    function dragoverContent(event: DragEvent) {
        if(!event.target || !(event.target instanceof HTMLElement)) {
            return;
        }

        let targetPanelIndex = getPanelIndex(true, event.target)

        // if the target is the same as the original panel,
        // we don't need to move the tab
        // The tab should stay in place
        if(targetPanelIndex === undefined
            || !movedTabInfo.value
            || movedTabInfo.value.panelIndex === targetPanelIndex){
            return
        }

        setTimeout(() => {
            dragover(event, true);
        }, 20)
    }

    function dragleavePanel(event: DragEvent) {
        dragleave(event, true);
    }

    function drop(event: DragEvent) {
        if(!event.target || !(event.target instanceof HTMLElement)) {
            return;
        }
        const targetPanelIndexAttrValue = event.target.getAttribute("data-panel-index") ?? event.target.closest(".editor-tabs")?.getAttribute("data-panel-index");
        if(!targetPanelIndexAttrValue) {
            return;
        }
        const targetPanelIndex = parseInt(targetPanelIndexAttrValue);

        const targetTabId = (event.target.classList.contains("editor-tab") ? event.target.getAttribute("data-tab-id") : "") ?? ""

        if(!movedTabInfo.value) {
            return;
        }

        if(!movedTabInfo.value.tab) {
            throw new Error("Tab is not defined");
        }

        moveTab(movedTabInfo.value, targetPanelIndex, targetTabId);
        cleanUp();
    }

    function getTargetTabIndex(targetPanelIndex: number, targetTabId?: string): number {
        const targetTabIndex = panels.value[targetPanelIndex].tabs.findIndex((tab) => tab.value === targetTabId)
        if(targetTabIndex === -1){
            return panels.value[targetPanelIndex].tabs.length;
        }
        return targetTabIndex;
    }

    function moveTab(movedTabInfo: TabInfo, targetPanelIndex: number, targetTabId?: string){
        const {tab: movedTab, panelIndex: originalPanelIndex, tabIndex} = movedTabInfo

        const targetTabIndex = getTargetTabIndex(targetPanelIndex, targetTabId);

        // In case of reordering of tabs we have to
        // account for cases where simulated tabs are present.
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
        }

        if(panels.value[originalPanelIndex].activeTab.value === movedTab.value){
            panels.value[originalPanelIndex].activeTab = panels.value[originalPanelIndex].tabs[0];
        }

        // add the tab to the target panel in-place of the hovered simulated tab
        panels.value[targetPanelIndex].tabs.splice(targetTabIndex + 1, 0, movedTab);
    }

    function destroyTab(panelIndex:number, tab: Tab){
        const panel = panels.value[panelIndex];
        const tabIndex = panel.tabs.findIndex((t) => t.value === tab.value);
        panel.tabs.splice(tabIndex, 1);
        if(panel.activeTab.value === tab.value){
            panel.activeTab = panel.tabs[0];
        }
        emit("removeTab", tab.value)
    }

    watch(panels, () => {
        let index = 0;
        for(const panel of panels.value){
            if(panel.tabs.length === 0){
                panels.value.splice(index, 1)
            }
            index++;
        }
    }, {deep: true})

    function splitPanel(panelIndex: number){
        const panel = panels.value[panelIndex];
        const newPanel = {
            tabs: [panel.activeTab],
            activeTab: panel.activeTab
        }
        panels.value.splice(panelIndex + 1, 0, newPanel)

        // get index of active tab in the original panel
        const activeTabIndex = panel.tabs.findIndex((tab) => tab.value === panel.activeTab.value)

        // set the active tab to the previous tab in the original panel
        panel.activeTab = panel.tabs[activeTabIndex - 1] ?? panel.tabs[activeTabIndex + 1]

        // remove the tab from the original panel
        panel.tabs.splice(activeTabIndex, 1)
    }
</script>

<style lang="scss" scoped>
    .editor-tabs-container{
        display: flex;
        justify-content: space-between;
        background-color: var(--ks-background-body);
        border-bottom: 1px solid var(--ks-border-primary);
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
        line-height: 1.5rem;
        overflow-x: auto;
        scrollbar-width: none;
        &.dragover {
            background-color: var(--ks-background-card-hover);
        }
    }

    .editor-tabs .editor-tab{
        padding: 3px .5rem;
        border: none;
        border-left: 1px solid var(--ks-border-primary);
        border-radius: 2px 2px 0 0;
        border-bottom: none;
        background-color: var(--ks-background-card);
        display: flex;
        flex-wrap:nowrap;
        white-space: nowrap;
        align-items: center;
        gap: .5rem;
        color: var(--ks-content-secondary);
        opacity: .6;
        .tab-icon{
            color: var(--ks-content-inactive);
        }
        &.active {
            opacity: 1;
            color: var(--ks-content-primary);
        }
        &.simulated{
            opacity: .2;
            box-shadow: 0 0 0px 1px var(--ks-border-primary);
            overflow: visible;
            z-index: 1;
            position: relative;
        }
        &.dirty-icon{
            font-size: 16px;
        }
    }

    .default-theme{
        .splitpanes__pane {
            background-color: var(--ks-background-card);
        }

        :deep(.splitpanes__splitter){
            border-left-color: var(--ks-border-primary);
            background-color: var(--ks-background-card);
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

    .splitpanes__pane{
        transition: none;
    }
</style>