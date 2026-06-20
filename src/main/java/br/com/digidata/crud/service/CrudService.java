package br.com.digidata.crud.service;

import br.com.digidata.crud.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

@RequiredArgsConstructor
public abstract class CrudService<T, ID>
        implements ICrudService<T, ID> {
    protected final JpaRepository<T, ID> repository;

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
        findById(id);
        return repository.save(entity);
    }

    @Override
    public void delete(ID id) {
        T entity = findById(id);
        repository.delete(entity);
    }


}