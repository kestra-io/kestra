<template>
    <div style="display:flex; align-items: center; justify-content: space-between;">
        <div class="tabs">
            <el-checkbox-group v-model="activeTabs">
                <el-checkbox-button v-for="element of EDITOR_ELEMENTS" :key="element.value" :value="element.value" :name="element.value">
                    <component class="tabs-icon" :is="element.button.icon" />
                    {{ element.button.label }}
                </el-checkbox-button>
            </el-checkbox-group>
        </div>
        <EditorButtonsWrapper />
    </div>
    <MultiPanelTabs v-model="panels" @remove-tab="removeTab" />
</template>

<script setup lang="ts">
    import {ref, watch, computed} from "vue";

    import {useRouteQuery} from "@vueuse/router";
    import MultiPanelTabs, {Panel} from "../MultiPanelTabs.vue";
    import EditorButtonsWrapper from "../inputs/EditorButtonsWrapper.vue";
    import {DEFAULT_ACTIVE_TABS, EDITOR_ELEMENTS} from "./panelDefinition";
    import {useCodeTabs} from "./useCodeTabs";



    const {codeTabs} = useCodeTabs();

    const activeTabsUrl = useRouteQuery("activeTabs", DEFAULT_ACTIVE_TABS)
    const previousActiveTabs = ref(activeTabsUrl.value)
    const activeTabs = computed({
        get: () => Array.isArray(activeTabsUrl.value) ? activeTabsUrl.value : [activeTabsUrl.value],
        set: (value) => activeTabsUrl.value = value
    })


    function getPanelFromValue(value: string): Panel{
        if(value === "code"){
            return {
                activeTab: codeTabs.value[0],
                tabs: codeTabs.value
            }
        }
        const element = EDITOR_ELEMENTS.find(e => e.value === value)!
        return {
            activeTab: element,
            tabs: [element]
        }
    }

    const panels = ref<Panel[]>(activeTabs.value.map((t: string) => getPanelFromValue(t)))

    function removeTab(tab: string){
        activeTabs.value = activeTabs.value.filter(t => t !== tab)
    }

    watch(activeTabs, (newVal) => {
        const previous = previousActiveTabs.value

        // get the tabs to add
        const toAdd = newVal.filter(t => !previous.includes(t))

        // remove the tabs
        for(const p of panels.value){
            p.tabs = p.tabs.filter(
                t => newVal.includes(t.value)
                    || (t.value.startsWith("code-") && newVal.includes("code"))
            )
        }

        // add the tabs
        for(const t of toAdd){
            panels.value.push(getPanelFromValue(t))
        }

        previousActiveTabs.value = newVal
    })
</script>

<style lang="scss" scoped>
    .tabs{
        padding: .5rem 1rem;
        border-bottom: 1px solid var(--ks-border-primary);
    }

    .tabs-icon {
        margin-right: .25rem;
        vertical-align: bottom;
    }
</style>
