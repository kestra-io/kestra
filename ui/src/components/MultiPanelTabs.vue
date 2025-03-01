<template>
    <Splitpanes class="default-theme">
        <Pane v-for="(panel, panelIndex) in panels" :key="panelIndex">
            <div
                class="editor-tabs"
                @dragenter.prevent="dragover"
                @dragleave.prevent="dragleave"
                @dragover.prevent
                @drop="drop"
                :data-panel-index="panelIndex"
            >
                <button
                    v-for="(tab, tabIndex) in panel.tabs"
                    :key="tab.value"
                    class="editor-tab"
                    draggable="true"
                    @dragstart="(e) => dragstart.bind(null, [panelIndex, tab.value])(e)"
                    @dragenter.prevent="dragover"
                    @dragleave.prevent="dragleave"
                    @dragover.prevent
                    @drop.stop="drop"
                    @click="panel.activeTabIndex = tabIndex"
                >
                    <component :is="tab.button.icon" />
                    {{ tab.button.label }}
                </button>
            </div>
            <component :is="panel.tabs[panel.activeTabIndex].component" />
        </Pane>
    </Splitpanes>
</template>

<script lang="ts" setup>
    import {ref} from "vue";
    import "splitpanes/dist/splitpanes.css"
    import {Splitpanes, Pane} from "splitpanes"

    const props = defineProps<{
        panelsDefinition: {
            tabs:{
                button: {
                    icon: any,
                    label: string
                },
                value: string,
                component: any
            }[]
        }[]
    }>()

    const panels = ref(props.panelsDefinition.map((panel) => {
        return {
            tabs: panel.tabs,
            activeTabIndex: 0
        }
    }))

    function dragstart(editorElementPath: [number, string], event: DragEvent) {
        event.dataTransfer?.setData("text/plain", JSON.stringify(editorElementPath));
    }

    function dragover(event: DragEvent) {
        if(event.target instanceof HTMLElement){
            event.target.classList.add("dragover");
        }
    }

    function dragleave(event: DragEvent) {
        if(event.target instanceof HTMLElement){
            event.target.classList.remove("dragover");
        }
    }

    function drop(event: DragEvent) {
        const [originalPanelIndex, tabId] = JSON.parse(event.dataTransfer?.getData("text/plain") ?? "[]");
        if(!event.target || !(event.target instanceof HTMLElement)) {
            return;
        }
        const targetPanelIndexAttrValue = event.target.getAttribute("data-panel-index") ?? event.target.closest(".editor-tabs")?.getAttribute("data-panel-index");
        if(!targetPanelIndexAttrValue) {
            return;
        }
        const targetPanelIndex = parseInt(targetPanelIndexAttrValue);

        // find the tab object
        const tabIndex = panels.value[originalPanelIndex].tabs.findIndex((tab) => tab.value === tabId);

        const movedTab = panels.value[originalPanelIndex].tabs[tabIndex];

        if(event.target.classList.contains("editor-tabs")){
            // add the tab to the target panel
            panels.value[targetPanelIndex].tabs.push(movedTab);
        } else if(event.target.classList.contains("editor-tab")) {
            //get the index of the hovered tab
            const hoveredTabIndex = Array.from(event.target.parentElement?.children ?? []).indexOf(event.target);

            // add the tab to the target panel just before the hovered tab
            panels.value[targetPanelIndex].tabs.splice(hoveredTabIndex, 0, movedTab);
        }

        // if the leaving panel after removing is empty, delete the panel
        if(panels.value[originalPanelIndex].tabs.length <= 1) {
            panels.value.splice(originalPanelIndex, 1);
        }else {
            // if the tab was the active tab, and that tab was the last one, set the active tab to the previous one
            if(panels.value[originalPanelIndex].activeTabIndex === tabIndex && panels.value[originalPanelIndex].tabs.length === tabIndex + 1) {
                panels.value[originalPanelIndex].activeTabIndex = tabIndex - 1;
            }

            // finally, remove the tab from the original panel
            panels.value[originalPanelIndex].tabs.splice(tabIndex, 1);
        }

        event.target.classList.remove("dragover");
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
        border-radius: 5px 5px 0 0;
        border-bottom: none;
        background-color: var(--ks-background-card);
        display: flex;
        flex-wrap:nowrap;
        white-space: nowrap;
        align-items: center;
        gap: .5rem;
        &.dragover {
            background-color: #e3e3e3;
        }
    }
</style>