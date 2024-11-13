<script lang="ts" setup>
    import {computed} from "vue";
    import MessageOutline from "vue-material-design-icons/MessageOutline.vue"
    import FileDocument from "vue-material-design-icons/FileDocument.vue"
    import Slack from "vue-material-design-icons/Slack.vue"
    import Github from "vue-material-design-icons/Github.vue"
    import Calendar from "vue-material-design-icons/Calendar.vue"

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"

    import {useStore} from "vuex";

    const store = useStore();

    const configs = computed(() => store.state.misc.configs);
</script>

<template>
    <div class="wrapper">
        <button>
            <MessageOutline class="buttonIcon" />News
        </button>
        <button>
            <FileDocument class="buttonIcon" />Docs
        </button>
        <button>
            <Slack class="buttonIcon" />Help<OpenInNew class="openIcon" />
        </button>
        <button>
            <Github class="buttonIcon" />Open an Issue<OpenInNew class="openIcon" />
        </button>
        <button>
            <Calendar class="buttonIcon" />Get a demo<OpenInNew class="openIcon" />
        </button>
        <div style="flex:1" />
        <span class="versionNumber">{{ configs.version }}</span>
    </div>
</template>

<style scoped>
.wrapper {
    width: 65px;
    padding: 16px;
    writing-mode: vertical-rl;
    text-orientation: mixed;
    border-left: 1px solid var(--el-border-color);
    display: flex;
    align-items: center;
    gap: 8px;
    background: var(--card-bg);
}

.wrapper button{
    background:  var(--bs-border-color);
    border-radius: 5px;
    border: 1px solid var(--el-border-color);
    padding: 10px 5px;
    width: 32px;
    display: block;
    line-height: normal;
}

.wrapper button:hover{
    border-color: var(--bs-primary);
}

.buttonIcon{
    transform: rotate(90deg);
    margin-bottom: 8px;
}

.openIcon{
    transform: rotate(90deg);
    margin-top: 8px;
    color: var(--bs-tertiary-color);
    font-size: 12px;
}

.versionNumber{
    font-size: 12px;
    color: var(--bs-tertiary-color);
}
</style>
