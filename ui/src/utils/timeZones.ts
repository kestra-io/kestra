type IntlWithSupportedValues = typeof Intl & {
    supportedValuesOf?: (key: "timeZone") => string[]
}

export function timeZones(): string[] {
    const supported = (Intl as IntlWithSupportedValues).supportedValuesOf?.("timeZone")
    return supported?.length ? supported : ["UTC"]
}
