<template>
    <div class="p-4" style="text-align: center;">
        <div class="mb-4">
            <ElButton size="large" @click="toast">
                El Message
            </ElButton>
        </div>

        <div class="my-2 flex flex-wrap items-center justify-center text-center">
            <div class="mb-4">
                <ElButton>Default</ElButton>
                <ElButton type="primary">
                    Primary
                </ElButton>
                <ElButton type="success">
                    Success
                </ElButton>
                <ElButton type="info">
                    Info
                </ElButton>
                <ElButton type="warning">
                    Warning
                </ElButton>
                <ElButton type="danger">
                    Danger
                </ElButton>
            </div>

            <div class="mb-4">
                <ElButton plain>
                    Plain
                </ElButton>
                <ElButton type="primary" plain>
                    Primary
                </ElButton>
                <ElButton type="success" plain>
                    Success
                </ElButton>
                <ElButton type="info" plain>
                    Info
                </ElButton>
                <ElButton type="warning" plain>
                    Warning
                </ElButton>
                <ElButton type="danger" plain>
                    Danger
                </ElButton>
            </div>

            <div class="mb-4">
                <ElButton round>
                    Round
                </ElButton>
                <ElButton type="primary" round>
                    Primary
                </ElButton>
                <ElButton type="success" round>
                    Success
                </ElButton>
                <ElButton type="info" round>
                    Info
                </ElButton>
                <ElButton type="warning" round>
                    Warning
                </ElButton>
                <ElButton type="danger" round>
                    Danger
                </ElButton>
            </div>

            <div>
                <ElButton :icon="Search" circle />
                <ElButton type="primary" :icon="Edit" circle />
                <ElButton type="success" :icon="Check" circle />
                <ElButton type="info" :icon="Message" circle />
                <ElButton type="warning" :icon="Star" circle />
                <ElButton type="danger" :icon="Delete" circle />
            </div>
        </div>

        <div style="display: flex;gap:1rem;justify-content: center;align-items: center; margin: 1rem;">
            <ElAlert
                v-for="type in alertTypes"
                :key="type"
                :type="type"
                :title="`${capitalize(type)} Alert`"
            />
        </div>

        <div style="display: flex;gap:1rem;justify-content: center;align-items: center; margin: 1rem;">
            <ElAlert
                v-for="type in alertTypes"
                :key="type"
                :type="type"
                :title="`Dark ${capitalize(type)} Alert`"
                effect="dark"
            />
        </div>

        <div>
            <span>Light</span>&nbsp;
            <ElTag v-for="t of ['success', 'warning', 'danger', 'info']" :key="t" :type="t" class="m-1">
                {{ t }}
            </ElTag>
        </div>
        <div>
            <span>Dark</span>&nbsp;
            <ElTag v-for="t of ['success', 'warning', 'danger', 'info']" :key="t" :type="t" effect="dark" class="m-1">
                {{ t }}
            </ElTag>
        </div>

        <div>
            <ElSwitch v-model="value1" />
            <ElSwitch
                v-model="value1"
                class="m-2"
                style="--ep-switch-on-color: black; --ep-switch-off-color: gray;"
            />
        </div>

        <div class="my-2">
            <ElInput v-model="input" class="m-2" style="width: 200px" />
            <ElDatePicker
                v-model="curDate"
                class="m-2"
                type="date"
                placeholder="Pick a day"
            />
        </div>

        <ElTable :data="tableData" style="width: 100%">
            <ElTableColumn prop="date" label="Date" width="180" />
            <ElTableColumn prop="name" label="Name" width="180" />
            <ElTableColumn prop="address" label="Address" />
        </ElTable>
        <div style="margin:1rem; display:flex; gap: 1rem; justify-content: center; align-items: center;">
            Single Select
            <ElSelect
                v-model="valueSelect"
                placeholder="Select"
                size="large"
                style="width: 240px"
            >
                <KsOption
                    v-for="item in options"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                />
            </ElSelect>
            <ElSelect v-model="valueSelect" placeholder="Select" style="width: 240px">
                <KsOption
                    v-for="item in options"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                />
            </ElSelect>
            <ElSelect
                v-model="valueSelect"
                placeholder="Select"
                size="small"
                style="width: 240px"
            >
                <KsOption
                    v-for="item in options"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                />
            </ElSelect>
        </div>
        <div style="margin:1rem; display:flex; gap: 1rem; justify-content: center; align-items: center;">
            Multiple Select
            <ElSelect
                v-model="valueMultiple"
                multiple
                placeholder="Select"
                style="width: 240px"
            >
                <KsOption
                    v-for="item in options"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                />
            </ElSelect>
        </div>

        <Tabs :tabs="tabs" :embedActiveTab="activeTab" @changed="(tab) => { if(tab.name) tabChanged({name:tab.name}) }" />
        <div>
            <div class="sub-title my-2 text-sm text-gray-600">
                list suggestions when activated
            </div>
            <ElAutocomplete
                v-model="state1"
                :fetchSuggestions="querySearch"
                clearable
                class="inline-input w-50"
                placeholder="Please Input"
            />
        </div>

        <div class="el-input el-input-file custom-upload">
            <form ref="importForm">
                <div class="el-input__wrapper">
                    <label for="importFlows">
                        <Upload /> Import
                    </label>
                    <input
                        id="importFlows"
                        class="el-input__inner"
                        type="file"
                        accept=".zip, .yml, .yaml"
                        ref="file"
                    >
                </div>
            </form>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {onMounted, ref} from "vue"
    import {KsMessage} from "@kestra-io/design-system"
    import {
        ElAlert,
        ElAutocomplete,
        ElButton,
        ElDatePicker,
        ElInput,
        ElSelect,
        ElSwitch,
        ElTable,
        ElTableColumn,
        ElTag,
    } from "element-plus"
    import Search from "vue-material-design-icons/SearchWeb.vue"
    import Edit from "vue-material-design-icons/Pencil.vue"
    import Check from "vue-material-design-icons/Check.vue"
    import Message from "vue-material-design-icons/Message.vue"
    import Star from "vue-material-design-icons/Star.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import Upload from "vue-material-design-icons/Upload.vue"
    import Tabs from "../../../src/components/Tabs.vue"

    const input = ref("")
    const curDate = ref(new Date())
    const value1 = ref(false)

    const alertTypes = ["success", "info", "warning", "error"] as const
    const capitalize = (value: string) => value.charAt(0).toUpperCase() + value.slice(1)


    function toast() {
        KsMessage.success("Hello")
    }

    const tableData = [
        {
            date: "2016-05-03",
            name: "Tom",
            address: "No. 189, Grove St, Los Angeles",
        },
        {
            date: "2016-05-02",
            name: "Tom",
            address: "No. 189, Grove St, Los Angeles",
        },
        {
            date: "2016-05-04",
            name: "Tom",
            address: "No. 189, Grove St, Los Angeles",
        },
        {
            date: "2016-05-01",
            name: "Tom",
            address: "No. 189, Grove St, Los Angeles",
        },
    ]

    const valueSelect = ref("")
    const valueMultiple = ref([])

    const options = [
        {
            value: "Option1",
            label: "Option1",
        },
        {
            value: "Option2",
            label: "Option2",
        },
        {
            value: "Option3",
            label: "Option3",
        },
        {
            value: "Option4",
            label: "Option4",
        },
        {
            value: "Option5",
            label: "Option5",
        },
    ]

    const tabs = [
        {
            title: "Tab 1",
            name: "first",
        },
        {
            title: "Tab 2",
            name: "second",
        },
        {
            title: "Tab 3",
            name: "third",
        },
    ]

    const activeTab = ref(tabs[0].name)

    function tabChanged(tab: {name:string}) {
        activeTab.value = tab.name
    }

    interface RestaurantItem {
        value: string
        link: string
    }

    const state1 = ref("")

    const restaurants = ref<RestaurantItem[]>([])
    const querySearch = (queryString: string, cb: any) => {
        const results = queryString
            ? restaurants.value.filter(createFilter(queryString))
            : restaurants.value
        // call callback function to return suggestions
        cb(results)
    }

    const createFilter = (queryString: string) => {
        return (restaurant: RestaurantItem) => {
            return (
                restaurant.value.toLowerCase().indexOf(queryString.toLowerCase()) === 0
            )
        }
    }

    const loadAll = () => {
        return [
            {value: "vue", link: "https://github.com/vuejs/vue"},
            {value: "element", link: "https://github.com/ElemeFE/element"},
            {value: "cooking", link: "https://github.com/ElemeFE/cooking"},
            {value: "mint-ui", link: "https://github.com/ElemeFE/mint-ui"},
            {value: "vuex", link: "https://github.com/vuejs/vuex"},
            {value: "vue-router", link: "https://github.com/vuejs/vue-router"},
            {value: "babel", link: "https://github.com/babel/babel"},
        ]
    }

    onMounted(() => {
        restaurants.value = loadAll()
    })
</script>

<style scoped>
.demo-tabs > :deep( .kel-tabs__content) {
  padding: 32px;
  color: #6b778c;
  font-size: 32px;
  font-weight: 600;
}
</style>