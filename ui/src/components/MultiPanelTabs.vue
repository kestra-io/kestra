<template>
    <Splitpanes class="default-theme">
        <Pane v-for="(panel, panelIndex) in panels" :key="panelIndex">
            <div
                class="editor-tabs"
                :class="{dragover: panel.dragover}"
                @dragleave.prevent="dragleavePanel"
                @dragenter.prevent="dragoverPanel"
                @dragover.prevent
                @drop="drop"
                :data-panel-index="panelIndex"
            >
                <template
                    v-for="tab in panel.tabs"
                    :key="tab.value"
                >
                    <button
                        v-if="!tab.potential"
                        class="editor-tab"
                        :class="{active: tab.value === panel.activeTab.value}"
                        draggable="true"
                        @dragstart="() => dragstart(panelIndex, tab.value)"
                        @dragend="cleanUp"
                        @dragenter.prevent.stop="dragover"
                        @dragover.prevent
                        @drop.stop="drop"
                        :data-tab-id="tab.value"
                        @click="panel.activeTab = tab"
                    >
                        <component :is="tab.button.icon" />
                        {{ tab.button.label }}
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
            <component :is="panel.activeTab.component" />
        </Pane>
    </Splitpanes>
</template>

<script lang="ts" setup>
    import {nextTick, ref} from "vue";
    import "splitpanes/dist/splitpanes.css"
    import {Splitpanes, Pane} from "splitpanes"

    interface Tab {
        button: {
            icon: any,
            label: string
        },
        potential?: boolean
        fromPanel?: boolean
        value: string,
        component: any
    }

    interface TabInfo {
        panelIndex: number,
        tabId: string,
        tabIndex: number,
        tab: Tab
    }

    const panels = defineModel<{
        tabs: Tab[],
        dragover?:boolean,
        activeTab: Tab,
    }[]>({
        required: true,
    })

    const movedTab = ref<TabInfo | null>(null);

    function dragstart(panelIndex: number, tabId: string) {
        const tabIndex = panels.value[panelIndex].tabs.findIndex((tab) => tab.value === tabId);
        movedTab.value = {panelIndex, tabId, tabIndex, tab: panels.value[panelIndex].tabs[tabIndex]}
    }

    function cleanUp(){
        nextTick(() => {
            movedTab.value = null
            panels.value = panels.value.map((panel) => {
                return {
                    ...panel,
                    dragover: false,
                    tabs: panel.tabs.filter((tab) => !tab.potential)
                }
            })
        })
    }

    function getPanelIndex(fromPanel: boolean, target: HTMLElement): number | undefined {
        let targetPanelIndex = 0;
        if (fromPanel) {
            targetPanelIndex = parseInt(target.getAttribute("data-panel-index") ?? "-1");
            if(targetPanelIndex < 0){
                return
            }
            panels.value[targetPanelIndex].dragover = true;

        } else {
            targetPanelIndex = parseInt(target.closest(".editor-tabs")?.getAttribute("data-panel-index") ?? "-1");
            if(targetPanelIndex < 0){
                return
            }
            const targetTabId = target.getAttribute("data-tab-id") ?? ""
            if(targetTabId === movedTab.value?.tab.value) {
                return
            }
        }

        return targetPanelIndex

    }

    function dragover(event: DragEvent, fromPanel: boolean = false) {
        if(!(event.target instanceof HTMLElement)){
            return
        }

        const targetPanelIndex = getPanelIndex(fromPanel, event.target);
        if(targetPanelIndex === undefined){
            return
        }

        const tabId = event.target.getAttribute("data-tab-id")

        const targetTabIndex = tabId
            ? panels.value[targetPanelIndex].tabs.findIndex((tab) => tab.value === tabId)
            : panels.value[targetPanelIndex].tabs.length;

        const movedTabInfo = movedTab.value;
        if(!movedTabInfo) {
            return;
        }

        // add a simulated tab to the target panel to see where the tab will be placed
        const tab = {
            component: () => null,
            value: "simulated-" + movedTabInfo.tab.value,
            fromPanel,
            potential:true,
            button: {
                ...movedTabInfo.tab.button,
            }
        }

        // avoid having multiple simulated tabs
        if(panels.value[targetPanelIndex].tabs.some(t => t.value === tab.value)) {
            // remove any already present simulated tab
            panels.value[targetPanelIndex].tabs = panels.value[targetPanelIndex].tabs.filter((t) => !t.potential)
        }

        panels.value[targetPanelIndex].tabs.splice(targetTabIndex, 0, tab);
    }


    function dragleave(event: DragEvent, fromPanel: boolean = false) {
        if(!(event.target instanceof HTMLElement)) {
            return
        }

        let targetPanelIndex = getPanelIndex(fromPanel, event.target)
        if(targetPanelIndex === undefined){
            return
        }

        // remove the simulated tab from the target panel
        panels.value[targetPanelIndex].tabs = panels.value[targetPanelIndex].tabs.filter((tab) => !tab.potential)
    }

    function dragoverPanel(event: DragEvent) {
        dragover(event, true);
    }

    function dragleavePanel(event: DragEvent) {
        // if the dragleave happens because the
        // new simulated tab appeared, do not remove it
        if(event.relatedTarget instanceof HTMLElement && event.relatedTarget.classList.contains("simulated")) {
            return
        }
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

        if(!movedTab.value) {
            return;
        }
        moveTab(movedTab.value, targetPanelIndex, targetTabId);
        cleanUp();
    }

    function moveTab(movedTabInfo: TabInfo, targetPanelIndex: number, targetTabId?: string){
        const {tab: movedTab, panelIndex: originalPanelIndex, tabIndex} = movedTabInfo

        const targetTabIndex = panels.value[targetPanelIndex].tabs.findIndex((tab) => tab.value === targetTabId);

        // In case of reordering of tabs we have to
        // account for cases where simulated tabs are present.
        // They will take a slot in the list
        if(targetPanelIndex === originalPanelIndex){
            if (targetTabIndex === tabIndex) {
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


        if (targetTabIndex === -1) {
            // if there is no target tab, add the tab to the end of the target panel
            panels.value[targetPanelIndex].tabs.push(movedTab);
        }else {
            // add the tab to the target panel in-place of the hovered simulated tab
            panels.value[targetPanelIndex].tabs.splice(targetTabIndex + 1, 0, movedTab);
        }

        // if the leaving panel after removing is empty, delete the panel
        if(panels.value[originalPanelIndex].tabs.length === 0) {
            panels.value.splice(originalPanelIndex, 1);
        }
    }
</script>

<style lang="scss" scoped>
    .editor-tabs {
        display: flex;
        padding: .25rem;
        padding-bottom: 0;
        border-bottom: 1px solid var(--ks-border-primary);
        &.dragover {
            background-color: #e3e3e3;
        }
    }

    .editor-tabs .editor-tab{
        padding: 0 .5rem;
        border: 1px solid var(--ks-border-primary);
        border-top-width: 4px;
        border-radius: 5px 5px 0 0;
        border-bottom: none;
        background-color: var(--ks-background-card);
        display: flex;
        flex-wrap:nowrap;
        white-space: nowrap;
        align-items: center;
        gap: .5rem;
        &.active {
            border-top-width: 1px;
        }
        &.simulated{
            opacity: .5;
        }
    }
</style>