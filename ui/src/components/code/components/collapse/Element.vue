<template>
    <div @click="handleClick" class="d-flex my-2 p-2 rounded element" :class="{'moved': moved}">
        <div class="me-2 icon">
            <TaskIcon :cls="element.type" :icons only-icon />
        </div>

        <div class="flex-grow-1 label">
            {{ taskIdentifier }}
        </div>

        <el-button
            @click.prevent.stop="emits('removeElement')"
            :icon="DeleteOutline"
            size="small"
            class="border-0"
        />
        <div v-if="elementIndex !== undefined" class="d-flex flex-column">
            <ChevronUp @click.prevent.stop="emits('moveElement', 'up')" />
            <ChevronDown @click.prevent.stop="emits('moveElement', 'down')" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue";
    import {useI18n} from "vue-i18n";
    import {usePluginsStore} from "../../../../stores/plugins";

    import {DeleteOutline, ChevronUp, ChevronDown} from "../../utils/icons";
    import {EDIT_TASK_FUNCTION_INJECTION_KEY} from "../../injectionKeys";

    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";

    const emits = defineEmits(["removeElement", "moveElement"]);

    const {t} = useI18n();

    const props = defineProps<{
        section: string;
        parentPathComplete: string;
        element: {
            id: string;
            type: string;
        };
        blockSchemaPath: string;
        elementIndex?: number;
        moved?: boolean;
    }>();

    const pluginsStore = usePluginsStore();

    const icons = computed(() => pluginsStore.icons);

    const editTask = inject(
        EDIT_TASK_FUNCTION_INJECTION_KEY,
        () => {},
    );

    const identifier = computed(() => {
        return props.element.id ?? props.element.type;
    });

    const taskIdentifier = computed(() => {
        return identifier.value ?? `<${t("no_code.unnamed")} ${props.elementIndex}>`;
    });

    const handleClick = () => {
        editTask(
            props.parentPathComplete,
            props.blockSchemaPath,
            props.elementIndex,
        );
    };
</script>

<style scoped lang="scss">
@import "../../styles/code.scss";

.element {
    cursor: pointer;
    background-color: $code-card-color;
    border: 1px solid $code-border-color;
    transition: all 0.2s ease-in-out;

    & > .icon {
        width: 1.25rem;
    }

    & > .label {
        color: inherit;
        font-size: $code-font-sm;
    }

    &.moved {
        background-color: var(--ks-button-background-secondary-active);
        border-color: var(--ks-border-active);
    }
}
</style>
