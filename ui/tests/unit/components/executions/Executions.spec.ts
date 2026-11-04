import {afterEach, describe, expect, test, vi} from "vitest";
import {ref} from "vue";
import {flushPromises, shallowMount} from "@vue/test-utils";

const route = {
    name: "executions/list",
    query: {},
    params: {},
};

const findExecutions = vi.fn().mockResolvedValue(undefined);

vi.mock("vue-router", () => ({
    useRoute: () => route,
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}));

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}));

vi.mock("../../../../src/composables/useRestoreUrl", () => ({
    default: () => ({loadInit: ref(true), saveRestoreUrl: vi.fn()}),
}));

vi.mock("../../../../src/composables/useRouteContext", () => ({
    default: vi.fn(),
}));

vi.mock("../../../../src/composables/useTableColumns", () => ({
    useTableColumns: () => ({visibleColumns: ref([]), updateVisibleColumns: vi.fn()}),
}));

vi.mock("../../../../src/composables/useSelectTableActions", () => ({
    useSelectTableActions: () => ({
        queryBulkAction: ref(false),
        selection: ref([]),
        handleSelectionChange: vi.fn(),
        toggleAllUnselected: vi.fn(),
        toggleAllSelection: vi.fn(),
    }),
}));

vi.mock("../../../../src/stores/executions", () => ({
    useExecutionsStore: () => ({
        findExecutions,
        executions: [],
        total: 50,
    }),
}));

vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({flow: undefined}),
}));

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {
            isAllowed: () => false,
            hasAnyActionOnAnyNamespace: () => false,
        },
    }),
}));

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {hiddenLabelsPrefixes: []}}),
}));

vi.mock("../../../../src/utils/toast", () => ({
    useToast: () => ({confirm: vi.fn(), success: vi.fn(), error: vi.fn()}),
}));

vi.mock("@kestra-io/ui-libs", () => ({
    State: {FAILED: "FAILED", SUCCESS: "SUCCESS", WARNING: "WARNING", CANCELLED: "CANCELLED", RUNNING: "RUNNING"},
    Status: {},
}));

const componentStub = vi.hoisted(() => ({default: {template: "<div />"}}));
const dataTableStub = vi.hoisted(() => ({default: {name: "DataTable", template: "<div />"}}));
vi.mock("../../../../src/components/layout/DataTable.vue", () => dataTableStub);
vi.mock("../../../../src/components/layout/SelectTable.vue", () => componentStub);
vi.mock("../../../../src/components/dashboard/sections/Sections.vue", () => componentStub);
vi.mock("../../../../src/components/layout/BulkSelect.vue", () => componentStub);
vi.mock("../../../../src/components/filter/components/KSFilter.vue", () => componentStub);
vi.mock("../../../../src/components/layout/TopNavBar.vue", () => componentStub);
vi.mock("../../../../src/components/labels/LabelInput.vue", () => componentStub);
vi.mock("../../../../src/components/flows/TriggerFlow.vue", () => componentStub);
vi.mock("../../../../src/components/flows/TriggerAvatar.vue", () => componentStub);
vi.mock("../../../../src/components/Id.vue", () => componentStub);
vi.mock("../../../../src/components/IconButton.vue", () => componentStub);
vi.mock("../../../../src/components/layout/Labels.vue", () => componentStub);
vi.mock("../../../../src/components/layout/DateAgo.vue", () => componentStub);
vi.mock("../../../../src/components/dashboard/sections/table/columns/Duration.vue", () => componentStub);

import DataTable from "../../../../src/components/layout/DataTable.vue";
import Executions from "../../../../src/components/executions/Executions.vue";

afterEach(() => {
    vi.clearAllMocks();
});

describe("Executions.vue embed pagination", () => {
    test("loads the requested page when the table changes page in embed mode", async () => {
        const wrapper = shallowMount(Executions, {
            props: {embed: true},
        });

        await flushPromises();
        wrapper.findComponent(DataTable).vm.$emit("page-changed", {page: 2, size: 25});
        await flushPromises();

        expect(findExecutions).toHaveBeenLastCalledWith(expect.objectContaining({page: 2, size: 25}));
    });
});
