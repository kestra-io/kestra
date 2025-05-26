import {FilterLanguage} from "../../composables/monaco/languages/filters/filterLanguage";

export default async () => ((await Promise.all(Object.values(
    import.meta.glob("../../composables/monaco/languages/filters/impl/*.ts", {import: "default", eager: true})
))) as (FilterLanguage | any)[]).filter(imported => imported instanceof FilterLanguage)