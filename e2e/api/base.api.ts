import {APIRequestContext} from "playwright/test"
import {shared} from "../fixtures/shared"

export class BaseApi {
    protected static get AUTH(): string {
        return `Basic ${Buffer.from(`${shared.username}:${shared.password}`).toString("base64")}`
    }

    protected readonly apiUrl: string

    constructor(public readonly request: APIRequestContext, protected readonly baseURL: string | undefined) {
        this.apiUrl = `${baseURL}/api/v1`
    }
}