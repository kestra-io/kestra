package io.kestra.core.models;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class SearchResult<T> {
    T model;
    List<String> fragments;
}
