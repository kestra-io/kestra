package org.kestra.core.repositories;

import io.micronaut.data.model.Pageable;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor
public class ArrayListTotal<T> extends ArrayList<T> {
    private long total;

    public static <T> ArrayListTotal<T> of(Pageable pageable, List<T> list) {
        int from = (pageable.getNumber() - 1) * pageable.getSize();
        int to = from + pageable.getSize();
        int size = list.size();

        to = Math.min(to, size);
        from = Math.min(from, size);

        return new ArrayListTotal<T>(list.subList(from, to), size);
    }

    public ArrayListTotal(List<T> list, long total) {
        super(list);
        this.total = total;
    }
}
