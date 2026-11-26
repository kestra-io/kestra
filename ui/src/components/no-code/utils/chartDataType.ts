import type {Schema} from "../components/tasks/getTaskComponent"
import {getValueAtJsonPath} from "../../../utils/utils"

/** The data source a chart definition is specialised for; a documented `data` holds its `$ref` inside `allOf`. */
export function chartDataType(chartSchema: Schema | undefined, fullSchema: object): string | undefined {
    const data = chartSchema?.properties?.data
    const ref = data?.$ref ?? data?.allOf?.find((branch) => branch.$ref)?.$ref
    const resolved = ref ? getValueAtJsonPath<Schema | undefined>(fullSchema, ref) : data
    return resolved?.properties?.type?.const
}
