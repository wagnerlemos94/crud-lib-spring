package br.com.digidata.crud.service;

import br.com.digidata.crud.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.List;

public abstract class CrudService<T, ID>
        implements ICrudService<T, ID> {
    protected final JpaRepository<T, ID> repository;

    public CrudService(JpaRepository<T, ID> repository) {
        this.repository = repository;
    }

    @Override
    public T findById(ID id) {

        return repository.findById(id)
                .orElseThrow(
                        () -> new ResourceNotFoundException(
                                "Registro não encontrado"
                        )
                );
    }

    @Override
    public List<T> findAll() {
        return repository.findAll();
    }

    @Override
    public T create(T entity) {
        return repository.save(entity);
    }

    @Override
    public T update(ID id, T entity) {

        T current = findById(id);

        BeanUtils.copyProperties(
                entity,
                current,
                getNullPropertyNames(entity)
        );
        return repository.save(current);
    }
    private String[] getNullPropertyNames(Object source) {

        BeanWrapper wrapper = new BeanWrapperImpl(source);

        return Arrays.stream(wrapper.getPropertyDescriptors())
                .map(PropertyDescriptor::getName)
                .filter(name -> !name.equals("class"))
                .filter(name -> {
                    try {
                        return wrapper.getPropertyValue(name) == null;
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toArray(String[]::new);
    }

    @Override
    public void delete(ID id) {
        T entity = findById(id);
        repository.delete(entity);
    }


}