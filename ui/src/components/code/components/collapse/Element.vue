<template>
    <div @click="handleClick" class="d-flex my-2 p-2 rounded element">
        <div class="me-2 icon">
            <TaskIcon :cls="props.element.type" :icons only-icon />
        </div>

        <div class="flex-grow-1 label">
            {{ props.element.id }}
        </div>

        <el-button
            @click.prevent.stop="emits('removeElement')"
            :icon="DeleteOutline"
            size="small"
            class="border-0"
        />
        <div class="d-flex flex-column">
            <ChevronUp @click.prevent.stop="emits('moveElement', 'up')" />
            <ChevronDown @click.prevent.stop="emits('moveElement', 'down')" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue";

    import {DeleteOutline, ChevronUp, ChevronDown} from "../../utils/icons";
    import {SECTION_INJECTION_KEY, TASKID_INJECTION_KEY} from "../../injectionKeys";

    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";

    const emits = defineEmits(["removeElement", "moveElement"]);

    const props = defineProps<{
        section: string;
        element: {
            id: string;
            type: string;
        };
    }>();

    import {useStore} from "vuex";
    const store = useStore();

    const icons = computed(() => store.state.plugin.icons);

    const sectionInjected = inject(SECTION_INJECTION_KEY, ref(""));
    const taskId = inject(TASKID_INJECTION_KEY, ref(""));

    const handleClick = () => {
        sectionInjected.value = props.section.toLowerCase();
        taskId.value = props.element.id;
    };
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";

.element {
    cursor: pointer;
    background-color: $code-card-color;
    border: 1px solid $code-border-color;

    & > .icon {
        width: 1.25rem;
    }

    & > .label {
        color: inherit;
        font-size: $code-font-sm;
    }
}
</style>
