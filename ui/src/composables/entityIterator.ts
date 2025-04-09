export type FetchResult<T> = { total: number, results: T[] };

export abstract class EntityIterator<T> {
    readonly fetchSize: number;
    private _total: number | undefined;
    private page = 0;
    private alreadyFetched: T[] = [];
    private buffered: T[] = [];
    readonly options: any;

    protected constructor(fetchSize: number, options?: any) {
        if (fetchSize <= 0) {
            throw new Error("fetchSize must be greater than 0");
        }
        this.fetchSize = fetchSize;
        this.options = options ?? {};
    }

    get total(): number | undefined {
        return this._total;
    }

    fetchOptions() {
        return {
            commit: false,
            sort: "id:asc",
            page: ++this.page,
            size: this.fetchSize,
            ...this.options
        }
    }

    abstract fetchCall(): Promise<FetchResult<T>>;

    stopCondition() {
        return this.total === this.alreadyFetched.length;
    }

    /**
     * If no buffer is available, fetches the next entity and returns the first entity while buffering the rest
     */
    async single(): Promise<T | undefined> {
        if (this.stopCondition() && this.buffered.length === 0) {
            return Promise.resolve(undefined);
        }

        if (this.buffered.length === 0) {
            this.buffered = await this.next();
        }

        return this.buffered.shift();
    }

    /**
     * Fetches the next batch of entities
     */
    async next(): Promise<T[]> {
        if (this.stopCondition()) {
            return Promise.resolve([]);
        }

        const entityFetch = await this.fetchCall();
        this._total = entityFetch.total;
        this.alreadyFetched = [...this.alreadyFetched, ...entityFetch.results];
        return entityFetch.results;
    }

    /**
     * Fetches all entities by iterating over the pages
     */
    async all(): Promise<T[]> {
        if (this.total === this.alreadyFetched.length) {
            return this.alreadyFetched;
        }

        await this.next();
        const entitiesFetchPromises: Promise<T[]>[] = [];

        for (let i = this.page; i < this.total! / this.fetchSize; i++) {
            entitiesFetchPromises.push(this.next());
        }

        await Promise.all(entitiesFetchPromises);
        return this.alreadyFetched;
    }
}