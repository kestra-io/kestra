const RUNNING_STATES = new Set(["CREATED", "RESTARTED", "RUNNING", "KILLING", "PAUSED", "RETRYING"]);

export class State {
    static readonly CREATED = "CREATED" as const;
    static readonly RESTARTED = "RESTARTED" as const;
    static readonly SUCCESS = "SUCCESS" as const;
    static readonly RUNNING = "RUNNING" as const;
    static readonly KILLING = "KILLING" as const;
    static readonly KILLED = "KILLED" as const;
    static readonly FAILED = "FAILED" as const;
    static readonly WARNING = "WARNING" as const;
    static readonly PAUSED = "PAUSED" as const;
    static readonly CANCELLED = "CANCELLED" as const;
    static readonly SKIPPED = "SKIPPED" as const;
    static readonly QUEUED = "QUEUED" as const;
    static readonly RETRYING = "RETRYING" as const;
    static readonly RETRIED = "RETRIED" as const;
    static readonly BREAKPOINT = "BREAKPOINT" as const;

    static isRunning(state: string): boolean {
        return RUNNING_STATES.has(state);
    }
}
