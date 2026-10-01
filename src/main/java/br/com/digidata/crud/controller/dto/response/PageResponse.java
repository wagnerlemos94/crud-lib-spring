package br.com.digidata.crud.controller.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable HTTP representation of a zero-based page. */
public record PageResponse<T>(List<T> content, int page, int size,
                              long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(List.copyOf(source.getContent()), source.getNumber(),
                source.getSize(), source.getTotalElements(), source.getTotalPages());
    }
}
