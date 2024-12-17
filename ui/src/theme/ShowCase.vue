<template>
    <center class="p-4">
        <div class="mb-4">
            <el-button size="large" @click="toast">
                El Message
            </el-button>

            <MessageBoxDemo />
        </div>

        <div class="my-2 flex flex-wrap items-center justify-center text-center">
            <div class="mb-4">
                <el-button>Default</el-button>
                <el-button type="primary">
                    Primary
                </el-button>
                <el-button type="success">
                    Success
                </el-button>
                <el-button type="info">
                    Info
                </el-button>
                <el-button type="warning">
                    Warning
                </el-button>
                <el-button type="danger">
                    Danger
                </el-button>
            </div>

            <div class="mb-4">
                <el-button plain>
                    Plain
                </el-button>
                <el-button type="primary" plain>
                    Primary
                </el-button>
                <el-button type="success" plain>
                    Success
                </el-button>
                <el-button type="info" plain>
                    Info
                </el-button>
                <el-button type="warning" plain>
                    Warning
                </el-button>
                <el-button type="danger" plain>
                    Danger
                </el-button>
            </div>

            <div class="mb-4">
                <el-button round>
                    Round
                </el-button>
                <el-button type="primary" round>
                    Primary
                </el-button>
                <el-button type="success" round>
                    Success
                </el-button>
                <el-button type="info" round>
                    Info
                </el-button>
                <el-button type="warning" round>
                    Warning
                </el-button>
                <el-button type="danger" round>
                    Danger
                </el-button>
            </div>

            <div>
                <el-button :icon="Search" circle />
                <el-button type="primary" :icon="Edit" circle />
                <el-button type="success" :icon="Check" circle />
                <el-button type="info" :icon="Message" circle />
                <el-button type="warning" :icon="Star" circle />
                <el-button type="danger" :icon="Delete" circle />
            </div>
        </div>

        <div style="display: flex;gap:1rem;justify-content: center;align-items: center; margin: 1rem;">
            <el-alert
                v-for="type in ['Success', 'Info', 'Warning', 'Error']"
                :key="type"
                :type="type.toLowerCase()"
                :title="`${type} Alert`"
                show-icon
            />
        </div>

        <div style="display: flex;gap:1rem;justify-content: center;align-items: center; margin: 1rem;">
            <el-alert
                v-for="type in ['Success', 'Info', 'Warning', 'Error']"
                :key="type"
                :type="type.toLowerCase()"
                :title="`Dark ${type} Alert`"
                effect="dark"
            />
        </div>

        <div>
            <el-tag type="success" class="m-1">
                Tag 1
            </el-tag>
            <el-tag type="warning" class="m-1">
                Tag 1
            </el-tag>
            <el-tag type="danger" class="m-1">
                Tag 1
            </el-tag>
            <el-tag type="info" class="m-1">
                Tag 1
            </el-tag>
        </div>

        <div>
            <el-switch v-model="value1" />
            <el-switch
                v-model="value1"
                class="m-2"
                style="--ep-switch-on-color: black; --ep-switch-off-color: gray;"
            />
        </div>

        <div class="my-2">
            <el-input v-model="input" class="m-2" style="width: 200px" />
            <el-date-picker
                v-model="curDate"
                class="m-2"
                type="date"
                placeholder="Pick a day"
            />
        </div>

        <el-table :data="tableData" style="width: 100%">
            <el-table-column prop="date" label="Date" width="180" />
            <el-table-column prop="name" label="Name" width="180" />
            <el-table-column prop="address" label="Address" />
        </el-table>
        <div style="margin:1rem; display:flex; gap: 1rem; justify-content: center; align-items: center;">
            <el-select
                v-model="valueSelect"
                placeholder="Select"
                size="large"
                style="width: 240px"
            >
                <el-option
                    v-for="item in options"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                />
            </el-select>
            <el-select v-model="valueSelect" placeholder="Select" style="width: 240px">
                <el-option
                    v-for="item in options"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                />
            </el-select>
            <el-select
                v-model="valueSelect"
                placeholder="Select"
                size="small"
                style="width: 240px"
            >
                <el-option
                    v-for="item in options"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                />
            </el-select>
            <el-select
                v-model="valueMultiple"
                multiple
                placeholder="Select"
                style="width: 240px"
            >
                <el-option
                    v-for="item in options"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                />
            </el-select>
        </div>

        <Tabs :tabs="tabs" :embed-active-tab="activeTab" @changed="tabChanged" />
    </center>
</template>

<script lang="ts" setup>
    import {getCurrentInstance, ref} from "vue"
    import {ElMessage} from "element-plus"
    import Search from "vue-material-design-icons/SearchWeb.vue"
    import Edit from "vue-material-design-icons/Pencil.vue"
    import Check from "vue-material-design-icons/Check.vue"
    import Message from "vue-material-design-icons/Message.vue"
    import Star from "vue-material-design-icons/Star.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    // @ts-expect-error Tabs is not yet TS
    import Tabs from "../components/Tabs.vue"

    const app = getCurrentInstance()?.appContext.config.globalProperties as any

    if(app){
        console.log("init")
        app.$router = {}
        app.$route = {params: {tab: "first"}}
    }

    const input = ref("")
    const curDate = ref(new Date())
    const value1 = ref(false)


    function toast() {
        ElMessage.success("Hello")
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
</script>

<style scoped>
.demo-tabs > :deep( .el-tabs__content) {
  padding: 32px;
  color: #6b778c;
  font-size: 32px;
  font-weight: 600;
}
</style>