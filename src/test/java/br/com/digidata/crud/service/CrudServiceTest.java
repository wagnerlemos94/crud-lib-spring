package br.com.digidata.crud.service;

import br.com.digidata.crud.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CrudServiceTest {
    private final Entity existing = new Entity();
    private final List<String> calls = new ArrayList<>();
    private final List<Boolean> transactionalCalls = new ArrayList<>();
    private final List<Boolean> readOnlyCalls = new ArrayList<>();
    private boolean found = true;
    private boolean failSave;
    private Pageable requestedPage;

    @SuppressWarnings("unchecked")
    private final JpaRepository<Entity, Long> repository = (JpaRepository<Entity, Long>)
            Proxy.newProxyInstance(JpaRepository.class.getClassLoader(), new Class<?>[]{JpaRepository.class},
                    (proxy, method, args) -> {
                        calls.add(method.getName());
                        transactionalCalls.add(TransactionSynchronizationManager.isActualTransactionActive());
                        readOnlyCalls.add(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                        return switch (method.getName()) {
                            case "findById" -> found ? Optional.of(existing) : Optional.empty();
                            case "findAll" -> {
                                if (args == null || args.length == 0) yield List.of(existing);
                                requestedPage = (Pageable) args[0];
                                yield new PageImpl<>(List.of(existing), requestedPage, 11);
                            }
                            case "save" -> {
                                if (failSave) throw new IllegalStateException("save failed");
                                yield args[0];
                            }
                            case "delete" -> null;
                            default -> throw new UnsupportedOperationException(method.getName());
                        };
                    });

    private CrudService<Entity, Long> service(Set<String> properties) {
        return new CrudService<>(repository) {
            @Override
            protected Set<String> updatableProperties() { return properties; }
        };
    }

    @Test
    void paginationIsDelegatedToRepositoryInReadOnlyTransaction() {
        Pageable requested = PageRequest.of(1, 5, Sort.by("description"));
        Page<Entity> result = transactionalService(new RecordingTransactions()).findAll(requested);
        assertSame(requested, requestedPage);
        assertEquals(11, result.getTotalElements());
        assertEquals(3, result.getTotalPages());
        assertEquals(List.of(existing), result.getContent());
        assertEquals(List.of(true), transactionalCalls);
        assertEquals(List.of(true), readOnlyCalls);
    }

    @Test
    void paginatedServiceRejectsUnpagedRequests() {
        assertThrows(IllegalArgumentException.class,
                () -> service(Set.of("description")).findAll(Pageable.unpaged()));
        assertTrue(calls.isEmpty());
    }

    @Test
    void replacesAllowedFieldsIncludingNullAndDefaultsButPreservesProtectedFields() {
        existing.setId(1L);
        existing.setVersion(3L);
        existing.setCreatedBy("admin");
        Object owner = new Object();
        existing.setOwner(owner);
        existing.setDescription("before");
        existing.setActive(true);
        existing.setCount(10);
        Entity incoming = new Entity();
        incoming.setId(99L);
        incoming.setVersion(99L);
        incoming.setCreatedBy("attacker");
        incoming.setOwner(new Object());

        assertSame(existing, service(Set.of("description", "active", "count")).update(1L, incoming));
        assertAll(
                () -> assertNull(existing.getDescription()),
                () -> assertFalse(existing.isActive()),
                () -> assertEquals(0, existing.getCount()),
                () -> assertEquals(1L, existing.getId()),
                () -> assertEquals(3L, existing.getVersion()),
                () -> assertEquals("admin", existing.getCreatedBy()),
                () -> assertSame(owner, existing.getOwner()),
                () -> assertEquals(List.of("findById", "save"), calls));
    }

    @Test
    void copiesNonNullAllowedValue() {
        Entity incoming = new Entity();
        incoming.setDescription("after");
        service(Set.of("description")).update(1L, incoming);
        assertEquals("after", existing.getDescription());
    }

    @Test
    void rejectsInvalidAllowlistBeforeChangingOrSavingEntity() {
        existing.setDescription("before");
        Entity incoming = new Entity();
        incoming.setDescription("after");
        assertThrows(IllegalArgumentException.class,
                () -> service(Set.of("description", "missing")).update(1L, incoming));
        assertEquals("before", existing.getDescription());
        assertEquals(List.of("findById"), calls);
    }

    @Test
    void missingUpdateAndDeleteDoNotWrite() {
        found = false;
        CrudService<Entity, Long> service = service(Set.of("description"));
        assertThrows(ResourceNotFoundException.class, () -> service.update(1L, new Entity()));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(1L));
        assertEquals(List.of("findById", "findById"), calls);
    }

    @SuppressWarnings("unchecked")
    private ICrudService<Entity, Long> transactionalService(RecordingTransactions transactions) {
        ProxyFactory factory = new ProxyFactory(service(Set.of("description")));
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(transactions);
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (ICrudService<Entity, Long>) factory.getProxy();
    }

    @Test
    void updateAndDeleteEachUseOneWriteTransaction() {
        RecordingTransactions transactions = new RecordingTransactions();
        ICrudService<Entity, Long> service = transactionalService(transactions);
        service.update(1L, new Entity());
        service.delete(1L);
        assertEquals(List.of("findById", "save", "findById", "delete"), calls);
        assertEquals(List.of(true, true, true, true), transactionalCalls);
        assertEquals(List.of(false, false, false, false), readOnlyCalls);
        assertEquals(2, transactions.begins);
        assertEquals(2, transactions.commits);
    }

    @Test
    void readOperationsAreReadOnlyAndCreateIsWritable() {
        RecordingTransactions transactions = new RecordingTransactions();
        ICrudService<Entity, Long> service = transactionalService(transactions);
        service.findById(1L);
        service.findAll();
        service.create(new Entity());
        assertEquals(List.of(true, true, true), transactionalCalls);
        assertEquals(List.of(true, true, false), readOnlyCalls);
    }

    @Test
    void saveFailureRollsBackTransaction() {
        failSave = true;
        RecordingTransactions transactions = new RecordingTransactions();
        assertThrows(IllegalStateException.class,
                () -> transactionalService(transactions).update(1L, new Entity()));
        assertEquals(1, transactions.rollbacks);
        assertEquals(0, transactions.commits);
    }

    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int begins;
        int commits;
        int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) { begins++; }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }

    public static class Entity {
        private Long id;
        private Long version;
        private String createdBy;
        private Object owner;
        private String description;
        private boolean active;
        private int count;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getVersion() { return version; }
        public void setVersion(Long version) { this.version = version; }
        public String getCreatedBy() { return createdBy; }
        public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
        public Object getOwner() { return owner; }
        public void setOwner(Object owner) { this.owner = owner; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
}
