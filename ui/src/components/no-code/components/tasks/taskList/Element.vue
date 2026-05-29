<template>
    <div @click="handleClick" class="d-flex my-2 p-2 rounded element" :class="{'moved': moved}">
        <div v-if="!['inputs', 'layout'].includes(props.parentPathComplete)" class="me-2 icon">
            <KsTaskIcon v-if="!isPlaceholder" :cls="element.type" :icons="pluginsStore.icons" onlyIcon />
            <PlusBoxOutline v-else class="placeholder-icon" />
        </div>

        <div class="flex-grow-1 label" :class="{placeholder: isPlaceholder}">
            {{ isPlaceholder ? placeholderLabel : (title ?? identifier) }}
        </div>

        <button v-if="playgroundStore.enabled && element.id && isTask" @click.stop="playgroundStore.runUntilTask(element.id)" type="button" class="playground-run-task">
            <PlayIcon />
        </button>

        <button
            v-if="!isPlaceholder"
            class="delete-element"
            type="button"
            @click.prevent.stop="emits('removeElement')"
        >
            <DeleteOutline />
        </button>
        <div v-if="elementIndex !== undefined" class="d-flex flex-column">
            <ChevronUp @click.prevent.stop="emits('moveElement', 'up')" />
            <ChevronDown @click.prevent.stop="emits('moveElement', 'down')" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import {useI18n} from "vue-i18n"
    import PlayIcon from "vue-material-design-icons/Play.vue"
    import PlusBoxOutline from "vue-material-design-icons/PlusBoxOutline.vue"
    import {usePluginsStore} from "../../../../../stores/plugins"
    import {usePlaygroundStore} from "../../../../../stores/playground"


    import {capitalCase} from "change-case"
    import {DeleteOutline, ChevronUp, ChevronDown} from "../../../utils/icons"
    import {
        EDIT_TASK_FUNCTION_INJECTION_KEY,
    } from "../../../injectionKeys"

    import {KsTaskIcon} from "@kestra-io/design-system"

    const emits = defineEmits(["removeElement", "moveElement"])

    const {t} = useI18n()

    const props = defineProps<{
        section: string;
        parentPathComplete: string;
        element: {
            id?: string;
            type?: string;
            on?: string;
        };
        blockSchemaPath: string;
        elementIndex?: number;
        typeFieldSchema: "on" | "type";
        moved?: boolean;
        title?: string
    }>()

    const pluginsStore = usePluginsStore()
    const playgroundStore = usePlaygroundStore()

    const isTask = computed(() => ["tasks", "task"].includes(props.parentPathComplete.split(".").pop() ?? "not-found"))

    const editTask = inject(EDIT_TASK_FUNCTION_INJECTION_KEY, () => {})

    const elementValue = computed(() => props.element[props.typeFieldSchema])

    const identifier = computed(() => {
        return props.element.id
            ?? props.element[props.typeFieldSchema]
            ?? `<${t("no_code.unnamed")} ${props.elementIndex}>`
    })

    const isPlaceholder = computed(() => !elementValue.value && props.elementIndex === undefined)

    const placeholderLabel = computed(() => {
        const field = props.parentPathComplete.split(".").pop()?.replace(/\[\d+\]$/, "") ?? ""
        const human = /^\d*$/.test(field) ? "" : capitalCase(field)
        return human ? t("no_code.add_field", {field: human}) : t("add")
    })

    const handleClick = () => {
        editTask(
            props.parentPathComplete,
            props.blockSchemaPath,
            props.elementIndex,
        )
    }
</script>

<style scoped lang="scss">
@import "../../../styles/code.scss";

.element {
    cursor: pointer;
    background-color: $code-card-color;
    border: 1px solid $code-border-color;
    transition: all 0.2s ease-in-out;

    &:hover {
        background-color: var(--ks-btn-secondary-bg-hover);
    }

    & > .icon {
        width: 1.25rem;
        display: flex;
        align-items: center;
        justify-content: center;

        .placeholder-icon {
            display: inline-flex;
            font-size: 1.25rem;
            color: var(--ks-text-link);
        }
    }

    & > .label {
        color: inherit;
        font-size: $code-font-sm;

        &.placeholder {
            color: var(--ks-text-link);
        }
    }

    &.moved {
        background-color: var(--ks-btn-secondary-bg-active);
        border-color: var(--ks-border-focus);
    }

    .playground-run-task{
        color: var(--ks-btn-primary-text);
        background-color: var(--ks-btn-primary-bg-default);
        height: 16px;
        width: 16px;
        font-size: 4px;
        display: flex;
        align-items: center;
        justify-content: center;
        margin-top: 4px;
        padding: 0;
        border: none;
    }

    .delete-element {        color: var(--ks-btn-primary-text);
        border: none;
        background-color: transparent;
    }
}
</style>
