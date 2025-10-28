package io.kestra.queue;

import io.kestra.core.utils.Disposable;

@SuppressWarnings("ClassCanBeRecord")
public class QueueDisposable implements Disposable {
    private final reactor.core.Disposable disposable;

    public QueueDisposable(reactor.core.Disposable disposable) {
        this.disposable = disposable;
    }

    @Override
    public void dispose() {
        this.disposable.dispose();
    }

    @Override
    public boolean isDisposed() {
        return this.disposable.isDisposed();
    }
}
