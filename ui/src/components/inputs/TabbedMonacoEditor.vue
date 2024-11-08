<template>
    <div>
        <div class="tabs">
            <div 
                v-for="tab in tabs" 
                :key="tab.name" 
                @click="selectTab(tab)" 
                :class="{ active: tab === currentTab }"
            >
                {{ tab.name }}
            </div>
        </div>
        <MonacoEditor 
            v-if="currentTab" 
            :value="currentTab.content" 
            :language="language" 
            :theme="theme" 
            @change="updateTabContent"
        />
    </div>
</template>

<script>
import { defineComponent } from "vue";
import MonacoEditor from "./MonacoEditor.vue";

export default defineComponent({
    components: { MonacoEditor },
    props: {
        tabs: {
            type: Array,
            required: true
        },
        language: {
            type: String,
            default: "javascript"
        },
        theme: {
            type: String,
            default: "vs-dark"
        }
    },
    data() {
        return {
            currentTab: null
        };
    },
    methods: {
        selectTab(tab) {
            this.currentTab = tab;
        },
        updateTabContent(newValue) {
            this.currentTab.content = newValue;
        }
    },
    mounted() {
        if (this.tabs.length) {
            this.currentTab = this.tabs[0];
        }
    }
});
</script>

<style scoped>
.tabs {
    display: flex;
    cursor: pointer;
}
.tabs div {
    padding: 10px;
    border: 1px solid #ccc;
}
.tabs div.active {
    background-color: #ddd;
}
</style>