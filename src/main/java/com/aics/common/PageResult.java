package com.aics.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResult<T> {
    private long total;
    private List<T> items;

    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(page.getTotalElements(), page.getContent());
    }

    public static <T> PageResult<T> of(long total, List<T> items) {
        return new PageResult<>(total, items);
    }
}
