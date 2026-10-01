package br.com.digidata.crud.service;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ICrudService<T, ID> {

    T findById(ID id);
    List<T> findAll();
    Page<T> findAll(Pageable pageable);
    T create(T entity);
    T update(ID id, T entity);
    void delete(ID id);
}
