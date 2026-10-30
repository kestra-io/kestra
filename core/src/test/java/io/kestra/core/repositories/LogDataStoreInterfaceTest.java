package io.kestra.core.repositories;

import java.util.List;
import java.util.OptionalInt;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LogDataStoreInterfaceTest {

    @Test
    void shouldDelegateToDeleteByFiltersAndReportAnUnknownCountByDefault() {
        // Given
        LogDataStoreInterface store = mock(LogDataStoreInterface.class);
        doCallRealMethod().when(store).deleteByFiltersAndCount(any(), any());
        List<QueryFilter> filters = List.of(QueryFilter.builder().field(QueryFilter.Field.FLOW_ID).operation(QueryFilter.Op.EQUALS).value("flow").build());

        // When
        OptionalInt count = store.deleteByFiltersAndCount("tenant", filters);

        // Then
        verify(store).deleteByFilters("tenant", filters);
        assertThat(count).isEmpty();
    }
}
