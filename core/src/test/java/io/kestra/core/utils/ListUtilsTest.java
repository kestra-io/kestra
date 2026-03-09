package io.kestra.core.utils;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListUtilsTest {

    @Test
    void emptyOnNull() {
        var list = ListUtils.emptyOnNull(null);
        assertThat(list).isNotNull();
        assertThat(list).isEmpty();

        list = ListUtils.emptyOnNull(List.of("1"));
        assertThat(list).isNotNull();
        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    void isEmpty() {
        assertThat(ListUtils.isEmpty(null)).isTrue();
        assertThat(ListUtils.isEmpty(Collections.emptyList())).isTrue();
        assertThat(ListUtils.isEmpty(List.of("1"))).isFalse();
    }

    @Test
    void concat() {
        List<String> list1 = List.of("1", "2");
        List<String> list2 = List.of("3", "4");

        assertThat(ListUtils.concat(list1, list2)).isEqualTo(List.of("1", "2", "3", "4"));
        assertThat(ListUtils.concat(list1, null)).isEqualTo(List.of("1", "2"));
        assertThat(ListUtils.concat(null, list2)).isEqualTo(List.of("3", "4"));
    }

    @Test
    void convertToList(){
        assertThat(ListUtils.convertToList(List.of(1, 2, 3))).isEqualTo(List.of(1, 2, 3));
        assertThrows(IllegalArgumentException.class, () -> ListUtils.convertToList("not a list"));
    }

    @Test
    void convertToListString(){
        assertThat(ListUtils.convertToListString(List.of("string1", "string2"))).isEqualTo(List.of("string1", "string2"));
        assertThat(ListUtils.convertToListString(List.of())).isEqualTo(List.of());
        assertThat(ListUtils.convertToListString(List.of(1, 2, 3))).isEqualTo(List.of("1", "2", "3"));

        assertThrows(IllegalArgumentException.class, () -> ListUtils.convertToListString("not a list"));
    }
}