package io.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.stream.Collectors.toCollection;

@Getter
@NoArgsConstructor
public class ArrayListTotal<T> extends ArrayList<T> {
    @Serial
    private static final long serialVersionUID = 1L;

    private long total;

    public static <T> ArrayListTotal<T> of(Pageable pageable, List<T> list) {
        int from = (pageable.getNumber() - 1) * pageable.getSize();
        int to = from + pageable.getSize();
        int size = list.size();

        to = Math.min(to, size);
        from = Math.min(from, size);

        return new ArrayListTotal<T>(list.subList(from, to), size);
    }

    public ArrayListTotal(long total) {
        this.total = total;
    }

    public ArrayListTotal(List<T> list, long total) {
        super(list);
        this.total = total;
    }

    public <R> ArrayListTotal<R> map(Function<T, R> map) {
        return this
            .stream()
            .map(map)
            .collect(toCollection(() -> new ArrayListTotal<R>(this.total)));
    }
}
