<template>
    <Handle type="source" :position="sourcePosition" />
    <BasicNode
        :id="id"
        :data="formattedData"
        :color="color"
        :icons="icons"
        :iconComponent="iconComponent"
    >
        <template #title-actions>
            <NodeMenu :actions="actions" />
        </template>
    </BasicNode>
    <Handle type="target" :position="targetPosition" />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {Handle, Position} from "@vue-flow/core"
    import {SECTIONS} from "@kestra-io/design-system"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import BasicNode from "./BasicNode.vue"
    import NodeMenu, {type NodeAction} from "./NodeMenu.vue"
    import {EVENTS} from "../utils/constants"
    import * as Utils from "../utils/utils"

    defineOptions({name: "Task", inheritAttrs: false})

    const {data, sourcePosition, targetPosition, id, icons, iconComponent} = defineProps<{
        data: any;
        sourcePosition: Position;
        targetPosition: Position;
        id: string;
        icons?: Record<string, any>;
        iconComponent?: any;
    }>()

    const emit = defineEmits([EVENTS.DELETE, EVENTS.EDIT, EVENTS.SHOW_DESCRIPTION, EVENTS.EXPAND])

    const {t} = useI18n()

    const color = computed(() => data.color ?? "primary")
    const triggerId = computed(() => Utils.afterLastDot(id))
    const formattedData = computed(() => ({
        ...data,
        unused: data.node?.triggerDeclaration?.disabled || data.node?.trigger?.disabled,
    }))

    const actions = computed<NodeAction[]>(() => {
        const list: NodeAction[] = []
        const trigger = data.node?.trigger ?? data.node?.triggerDeclaration

        if (trigger?.description) {
            list.push({
                key: "description",
                label: t("show description"),
                icon: InformationOutline,
                onClick: () => emit(EVENTS.SHOW_DESCRIPTION, {id: triggerId.value, description: trigger.description}),
            })
        }
        if (!data.isReadOnly) {
            list.push({
                key: "edit",
                label: t("edit"),
                icon: Pencil,
                onClick: () => emit(EVENTS.EDIT, {task: data.node.triggerDeclaration, section: SECTIONS.TRIGGERS}),
            })
            list.push({
                key: "delete",
                label: t("delete"),
                icon: Delete,
                danger: true,
                divided: true,
                onClick: () => emit(EVENTS.DELETE, {id: triggerId.value, section: SECTIONS.TRIGGERS}),
            })
        }

        return list
    })
</script>
