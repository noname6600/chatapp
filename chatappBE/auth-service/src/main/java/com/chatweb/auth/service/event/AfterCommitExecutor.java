package com.chatweb.auth.service.event;

public interface AfterCommitExecutor {

    void runAfterCommit(String operationName, Runnable action);
}
