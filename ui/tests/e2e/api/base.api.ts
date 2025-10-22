import {APIRequestContext} from "playwright/test";
import {shared} from "../fixtures/shared";

export class BaseApi {
    protected static readonly BASED_PAIR = Buffer.from(`${shared.username}:${shared.password}`).toString("base64");
    protected static readonly AUTH = `Basic ${BaseApi.BASED_PAIR}`;

    protected readonly apiUrl: string;

    constructor(public readonly request: APIRequestContext, protected readonly baseURL: string | undefined) {
        this.apiUrl = `${baseURL}/api/v1/main`;
    }
}