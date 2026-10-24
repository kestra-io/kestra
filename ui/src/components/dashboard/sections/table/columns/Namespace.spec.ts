import {describe, expect, test} from "vitest"
import {KsEntityLink} from "@kestra-io/design-system"
import {i18nMount} from "../../../../../../tests/unit/i18nMount"
import NamespaceColumn from "./Namespace.vue"

describe("dashboard table Namespace column", () => {
    test("renders the entity link with the field value", () => {
        const wrapper = i18nMount(NamespaceColumn, {props: {field: "company.team"}})

        expect(wrapper.findComponent(KsEntityLink).props("value")).toBe("company.team")
    })

    test("targets namespaces/update with the namespace as the id param", () => {
        const wrapper = i18nMount(NamespaceColumn, {props: {field: "company.team"}})

        expect(wrapper.findComponent(KsEntityLink).props("to")).toEqual({
            name: "namespaces/update",
            params: {id: "company.team"},
        })
    })

    test("renders without an icon", () => {
        const wrapper = i18nMount(NamespaceColumn, {props: {field: "company.team"}})

        expect(wrapper.findComponent(KsEntityLink).props("noIcon")).toBe(true)
    })

    test("renders nothing when the field is undefined or empty", () => {
        expect(i18nMount(NamespaceColumn, {props: {field: undefined}}).findComponent(KsEntityLink).exists()).toBe(false)
        expect(i18nMount(NamespaceColumn, {props: {field: ""}}).findComponent(KsEntityLink).exists()).toBe(false)
    })
})
