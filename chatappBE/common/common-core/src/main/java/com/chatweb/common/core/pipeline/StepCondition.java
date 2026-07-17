package com.chatweb.common.core.pipeline;

@FunctionalInterface
public interface StepCondition<C> {

    boolean test(C context);

}
