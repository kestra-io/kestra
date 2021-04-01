package io.kestra.core.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@AllArgsConstructor
@Getter
public class SearchResult<T> {
    T model;
    List<String> fragments;
}
