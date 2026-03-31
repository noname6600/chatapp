package com.example.common.core.pipeline;

@FunctionalInterface
public interface StepCondition<C> {

    boolean test(C context);

}
