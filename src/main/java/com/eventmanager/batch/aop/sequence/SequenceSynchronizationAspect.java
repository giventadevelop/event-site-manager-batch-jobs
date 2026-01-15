package com.eventmanager.batch.aop.sequence;

import com.eventmanager.batch.service.SequenceSynchronizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Aspect for handling duplicate key constraint violations by synchronizing
 * the sequence_generator sequence and retrying the operation.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
@Order(1)
public class SequenceSynchronizationAspect {

    private final SequenceSynchronizationService sequenceSynchronizationService;

    @Pointcut(
        "execution(* org.springframework.data.repository.CrudRepository.save(..)) || " +
        "execution(* org.springframework.data.jpa.repository.JpaRepository.save(..)) || " +
        "execution(* org.springframework.data.jpa.repository.JpaRepository.saveAndFlush(..)) || " +
        "execution(* org.springframework.data.repository.CrudRepository.saveAll(..))"
    )
    public void repositorySavePointcut() {
        // Pointcut for repository save operations
    }

    @Pointcut("within(com.eventmanager.batch.repository..*)")
    public void repositoryPointcut() {
        // Pointcut for repositories in this project
    }

    @Around("repositorySavePointcut() && repositoryPointcut()")
    public Object handleDuplicateKeyViolation(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();

        try {
            return joinPoint.proceed();
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateKeyViolation(e)) {
                log.warn(
                    "Duplicate key violation detected in {}.{}(). Synchronizing sequence_generator and retrying...",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    methodName,
                    e
                );

                try {
                    Long newSequenceValue = sequenceSynchronizationService.synchronizeSequence();
                    log.info(
                        "Sequence synchronized to value: {}. Retrying {}.{}() operation...",
                        newSequenceValue,
                        joinPoint.getSignature().getDeclaringTypeName(),
                        methodName
                    );

                    Object result = joinPoint.proceed();
                    log.info(
                        "Successfully completed {}.{}() after sequence synchronization",
                        joinPoint.getSignature().getDeclaringTypeName(),
                        methodName
                    );
                    return result;
                } catch (Exception retryException) {
                    log.error(
                        "Failed to execute {}.{}() even after sequence synchronization",
                        joinPoint.getSignature().getDeclaringTypeName(),
                        methodName,
                        retryException
                    );
                    throw new RuntimeException(
                        String.format(
                            "Failed to save entity due to duplicate key constraint. " +
                                "Sequence synchronization attempted but failed: %s",
                            retryException.getMessage()
                        ),
                        retryException
                    );
                }
            }

            throw e;
        }
    }

    private boolean isDuplicateKeyViolation(DataIntegrityViolationException e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }

        String message = e.getMessage().toLowerCase();
        String causeMessage = e.getCause() != null && e.getCause().getMessage() != null
            ? e.getCause().getMessage().toLowerCase()
            : "";

        return (message.contains("duplicate key value violates unique constraint") ||
                causeMessage.contains("duplicate key value violates unique constraint")) &&
               (message.contains("pkey") || causeMessage.contains("pkey") ||
                message.contains("primary key") || causeMessage.contains("primary key"));
    }
}
