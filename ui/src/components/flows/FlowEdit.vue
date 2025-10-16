<template>
  <TopNavBar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb">
    <template #additional-right v-if="canSave || canDelete || canExecute">
      <ul>
        <li>
          <el-button
            :icon="icons.Delete"
            size="large"
            v-if="canDelete"
            @click="deleteFile"
          >
            {{ $t("delete") }}
          </el-button>
        </li>

        <li>
          <router-link
            v-if="flowStore.flow && canCreate"
            :to="{ name: 'flows/create', query: { copy: true } }"
          >
            <el-button :icon="icons.ContentCopy" size="large">
              {{ $t("copy") }}
            </el-button>
          </router-link>
        </li>

        <li>
          <TriggerFlow
            v-if="flowStore.flow && canExecute"
            :disabled="flowStore.flow.disabled"
            :flowId="flowStore.flow.id"
            type="default"
            :namespace="flowStore.flow.namespace"
          />
        </li>

        <li>
          <el-button
            class="edit-flow-save-button"
            :icon="icons.ContentSave"
            size="large"
            @click="save"
            v-if="canSave"
            type="primary"
          >
            {{ $t("save") }}
          </el-button>
        </li>
      </ul>
    </template>
  </TopNavBar>

  <div class="mt-3 edit-flow-div">
    <editor
      v-model="content"
      schemaType="flow"
      lang="yaml"
      @save="save"
      @update:model-value="onChange"
      @cursor="updatePluginDocumentation"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, shallowRef, onMounted, onBeforeUnmount } from "vue";
import { useRoute } from "vue-router";
import { useI18n } from "vue-i18n";
import { useCoreStore } from "../../stores/core";
import { useFlowStore } from "../../stores/flow";
import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
import ContentSave from "vue-material-design-icons/ContentSave.vue";
import Delete from "vue-material-design-icons/Delete.vue";
import TriggerFlow from "./TriggerFlow.vue";
import TopNavBar from "../layout/TopNavBar.vue";
import flowTemplateEdit from "../../mixins/flowTemplateEdit"; // We'll reuse its logic manually

// ====== STORES ======
const coreStore = useCoreStore();
const flowStore = useFlowStore();
const route = useRoute();
const { t } = useI18n();

// ====== ICONS ======
const icons = {
  ContentCopy: shallowRef(ContentCopy),
  ContentSave: shallowRef(ContentSave),
  Delete: shallowRef(Delete),
};

// ====== REFS & STATE ======
const dataType = ref("flow");
const lastChangeWasGuided = ref(false);

// These reactive refs will hold editor data (from the mixin)
const content = ref<string>("");
const canSave = ref<boolean>(false);
const canDelete = ref<boolean>(false);
const canExecute = ref<boolean>(false);
const canCreate = ref<boolean>(false);
const routeInfo = ref<{ title: string; breadcrumb: string[] }>({
  title: "",
  breadcrumb: [],
});

// ====== IMPORT MIXIN LOGIC (manually replicated) ======
// Assuming `flowTemplateEdit` provides methods like `save`, `deleteFile`, `loadFile`, `onChange`, `updatePluginDocumentation`
// If you know the exact functions, replace the stubs below.

const save = () => {
  console.log("Saving flow...");
  // implement save logic from mixin
};

const deleteFile = () => {
  console.log("Deleting flow...");
  // implement delete logic
};

const loadFile = () => {
  console.log("Loading flow...");
  // implement load logic
};

const onChange = () => {
  console.log("Editor content changed");
};

const updatePluginDocumentation = () => {
  console.log("Update plugin docs...");
};

// ====== TOUR HANDLING ======
const stopTour = () => {
  const guidedTour = (window as any).$tours?.["guidedTour"];
  guidedTour?.stop();
  coreStore.guidedProperties = {
    ...coreStore.guidedProperties,
    tourStarted: false,
  };
};

// ====== LIFECYCLE ======
onMounted(() => {
  loadFile();

  setTimeout(() => {
    if (
      !coreStore.guidedProperties.tourStarted &&
      localStorage.getItem("tourDoneOrSkip") !== "true" &&
      flowStore.total === 0
    ) {
      (window as any).$tours?.["guidedTour"]?.start();
    }
  }, 200);

  window.addEventListener("popstate", stopTour);
});

onBeforeUnmount(() => {
  window.removeEventListener("popstate", stopTour);
});
</script>

<style scoped>
.edit-flow-div {
  margin-top: 1rem;
}

.edit-flow-save-button {
  margin-left: 0.5rem;
}
</style>
