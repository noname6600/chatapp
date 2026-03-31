package com.example.common.core.pipeline;


import java.util.*;

public class PipelineGraphResolver<C> {

    public List<PipelineStepDescriptor<C>> resolve(
            List<PipelineStepDescriptor<C>> descriptors
    ) {

        Map<Class<?>, PipelineStepDescriptor<C>> stepMap = new HashMap<>();

        for (PipelineStepDescriptor<C> d : descriptors) {
            stepMap.put(d.getStepClass(), d);
        }

        Map<Class<?>, Set<Class<?>>> graph = new HashMap<>();
        Map<Class<?>, Integer> indegree = new HashMap<>();

        for (PipelineStepDescriptor<C> d : descriptors) {

            graph.putIfAbsent(d.getStepClass(), new HashSet<>());
            indegree.putIfAbsent(d.getStepClass(), 0);

        }

        for (PipelineStepDescriptor<C> d : descriptors) {

            for (Class<? extends PipelineStep<?>> dep : d.getRunAfter()) {

                if (!stepMap.containsKey(dep)) {
                    throw new IllegalStateException(
                            "Missing pipeline step dependency: " + dep.getSimpleName()
                    );
                }

                graph.get(dep).add(d.getStepClass());

                indegree.put(
                        d.getStepClass(),
                        indegree.getOrDefault(d.getStepClass(), 0) + 1
                );
            }
        }

        Queue<Class<?>> queue = new LinkedList<>();

        for (Map.Entry<Class<?>, Integer> entry : indegree.entrySet()) {

            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }

        }

        List<PipelineStepDescriptor<C>> sorted = new ArrayList<>();

        while (!queue.isEmpty()) {

            Class<?> stepClass = queue.poll();

            sorted.add(stepMap.get(stepClass));

            for (Class<?> next : graph.getOrDefault(stepClass, Set.of())) {

                indegree.put(next, indegree.get(next) - 1);

                if (indegree.get(next) == 0) {
                    queue.add(next);
                }

            }

        }

        if (sorted.size() != descriptors.size()) {

            throw new IllegalStateException(
                    "Circular dependency detected in pipeline steps"
            );

        }

        return sorted;
    }
}
