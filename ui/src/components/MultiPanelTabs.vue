<template>
    <Splitpanes class="default-theme">
        <Pane v-for="(panel, panelIndex) in panels" :key="panelIndex">
            <div class="editor-tabs" @dragenter.stop.prevent="dragover" @dragleave.stop.prevent="dragleave" @drop="drop" :data-panel-index="panelIndex">
                <button v-for="(tab, tabIndex) in panel.tabs" :key="tab.value" class="editor-tab" draggable="true" @dragstart="(e) => dragstart.bind(null, tab.value)(e)" @click="panel.activeTabIndex = tabIndex">
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

    function dragstart(editorElementName: string, event: DragEvent) {
        event.dataTransfer?.setData("text/plain", editorElementName);
    }

    function dragover(event: DragEvent) {
        event.preventDefault();
        event.stopPropagation();
        if(event.target instanceof HTMLElement){
            if(event.target.classList.contains("editor-tabs")) {
                event.target.style.backgroundColor = "var(--ks-background-info)";
            }
        }
    }

    function dragleave(event: DragEvent) {
        event.preventDefault();
        event.stopPropagation();
        if(event.target instanceof HTMLElement){
            event.target.style.backgroundColor = "";
        }
    }

    function drop(event: DragEvent) {
        console.log("drop", event)
        const data = event.dataTransfer?.getData("text/plain");
        console.log("data", data)
    }
</script>

<style lang="scss" scoped>
    .editor-tabs {
        display: flex;
        padding: .25rem;
        padding-bottom: 0;
        border-bottom: 1px solid var(--ks-border-primary);
    }

    .editor-tabs .editor-tab{
        padding: 0 .5rem;
        border: 1px solid var(--ks-border-primary);
        border-radius: 5px 5px 0 0;
        border-bottom: none;
        background-color: var(--ks-background-card);
        display: flex;
        align-items: center;
        gap: .5rem;
    }
</style>