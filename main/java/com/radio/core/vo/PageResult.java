package com.radio.core.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 通用分页返回对象
 */
@Data
public class PageResult<T> {

    private Long current;
    private Long size;
    private Long total;
    private Long pages;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setCurrent(page == null ? 1L : page.getCurrent());
        result.setSize(page == null ? 10L : page.getSize());
        result.setTotal(page == null ? 0L : page.getTotal());
        result.setPages(page == null ? 0L : page.getPages());
        result.setRecords(page == null ? Collections.emptyList() : page.getRecords());
        return result;
    }
}