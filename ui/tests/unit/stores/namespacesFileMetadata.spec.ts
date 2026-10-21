import {beforeEach, describe, expect, test, vi} from "vitest";
import {AxiosError, type InternalAxiosRequestConfig} from "axios";
import {createPinia, setActivePinia} from "pinia";

const {adapter} = vi.hoisted(() => ({adapter: vi.fn()}));

vi.mock("vue-router", () => ({
    routerKey: Symbol("router"),
    useRouter: () => ({push: vi.fn(), replace: vi.fn()}),
}));
vi.mock("override/utils/route", () => ({
    apiUrl: () => "http://localhost:8080/api/v1/main",
    apiUrlWithTenant: () => "http://localhost:8080/api/v1/main",
}));
vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({isLogged: true}),
}));
vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {edition: "OSS"}}),
}));
vi.mock("../../../src/utils/axios", async (importOriginal) => {
    const actual = await importOriginal<typeof import("../../../src/utils/axios")>();
    const instance = actual.createAxios(undefined, true);
    instance.defaults.adapter = (config) => adapter(config);
    return {...actual, useAxios: () => instance};
});

const {useBaseNamespacesStore} = await import("../../../src/composables/useBaseNamespaces");
const {useCoreStore} = await import("../../../src/stores/core");

describe("namespaces store fileMetadata", () => {
    beforeEach(() => {
        setActivePinia(createPinia());
        adapter.mockReset();
    });

    test("rejects without switching the page to not found when the file is gone", async () => {
        adapter.mockImplementation((config: InternalAxiosRequestConfig) => Promise.reject(new AxiosError(
            "Request failed with status code 404",
            AxiosError.ERR_BAD_REQUEST,
            config,
            {},
            {status: 404, statusText: "Not Found", data: {}, headers: {}, config},
        )));

        await expect(useBaseNamespacesStore().fileMetadata({namespace: "io.kestra.test", path: "moved.py"}))
            .rejects.toBeInstanceOf(AxiosError);

        expect(useCoreStore().error).toBeUndefined();
    });
});
