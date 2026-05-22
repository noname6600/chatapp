package com.chatweb.auth.service.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Slf4j
public class TransactionalAfterCommitExecutor implements AfterCommitExecutor {

    @Override
    public void runAfterCommit(String operationName, Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(operationName, action);
                }
            });
            return;
        }

        runSafely(operationName, action);
    }

    private void runSafely(String operationName, Runnable action) {
        try {
            action.run();
        } catch (Exception ex) {
            log.error("after_commit_action_failed operation={} reason={}", operationName, ex.getMessage(), ex);
        }
    }
}
