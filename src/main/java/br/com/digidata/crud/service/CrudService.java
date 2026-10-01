package br.com.digidata.crud.service;

import br.com.digidata.crud.exception.ResourceNotFoundException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Transactional(readOnly = true)
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
    public Page<T> findAll(Pageable pageable) {
        Objects.requireNonNull(pageable, "pageable must not be null");
        if (pageable.isUnpaged()) {
            throw new IllegalArgumentException("A paged request is required");
        }
        return repository.findAll(pageable);
    }

    @Override
    @Transactional
    public T create(T entity) {
        return repository.save(entity);
    }

    @Override
    @Transactional
    public T update(ID id, T entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        T current = findById(id);
        Set<String> writable = Set.copyOf(updatableProperties());
        BeanWrapper source = new BeanWrapperImpl(entity);
        BeanWrapper target = new BeanWrapperImpl(current);
        Set<String> beanProperties = Arrays.stream(source.getPropertyDescriptors())
                .map(PropertyDescriptor::getName)
                .collect(java.util.stream.Collectors.toSet());
        for (String name : writable) {
            if (name.equals("class") || !beanProperties.contains(name)
                    || !source.isReadableProperty(name) || !target.isWritableProperty(name)) {
                throw new IllegalArgumentException("Invalid updatable property: " + name);
            }
        }
        BeanUtils.copyProperties(
                entity,
                current,
                beanProperties.stream()
                        .filter(name -> !writable.contains(name))
                        .toArray(String[]::new)
        );
        return repository.save(current);
    }

    /**
     * Explicit allowlist of writable JavaBean properties, for example
     * {@code Set.of("name", "description", "active")}.
     * Never include identifiers, versions, audit fields or protected relationships.
     * Every listed field is replaced, including null and primitive default values.
     * The request mapper must supply the complete writable state for PUT.
     * Use entity versioning (e.g. JPA {@code @Version}) when concurrent updates
     * must be detected; the transaction alone does not prevent lost updates.
     */
    protected abstract Set<String> updatableProperties();

    @Override
    @Transactional
    public void delete(ID id) {
        T entity = findById(id);
        repository.delete(entity);
    }


}
