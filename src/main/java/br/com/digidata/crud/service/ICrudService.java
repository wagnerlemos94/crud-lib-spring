package br.com.digidata.crud.service;

import java.util.List;

public interface ICrudService<T, ID> {

    T findById(ID id);
    List<T> findAll();
    T create(T entity);
    T update(ID id, T entity);
    void delete(ID id);
}