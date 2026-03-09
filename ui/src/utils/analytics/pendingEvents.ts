export type PendingItem<TData, TOptions> = {
    data: TData;
    options: TOptions;
    enqueuedAt: number;
};

export class PendingEventsBuffer<TData, TOptions> {
    private readonly maxItems: number;
    private readonly maxAgeMs: number;
    private items: PendingItem<TData, TOptions>[] = [];

    constructor(options: {maxItems: number; maxAgeMs: number}) {
        this.maxItems = options.maxItems;
        this.maxAgeMs = options.maxAgeMs;
    }

    get length(): number {
        return this.items.length;
    }

    clear() {
        this.items = [];
    }

    prune(now = Date.now()) {
        if (this.items.length === 0) return;
        const cutoff = now - this.maxAgeMs;
        this.items = this.items.filter((item) => item.enqueuedAt >= cutoff);
    }

    enqueue(data: TData, options: TOptions, now = Date.now()) {
        this.prune(now);
        if (this.items.length >= this.maxItems) {
            this.items.shift();
        }
        this.items.push({data, options, enqueuedAt: now});
    }

    drain(now = Date.now()): PendingItem<TData, TOptions>[] {
        this.prune(now);
        const drained = this.items;
        this.items = [];
        return drained;
    }
}

