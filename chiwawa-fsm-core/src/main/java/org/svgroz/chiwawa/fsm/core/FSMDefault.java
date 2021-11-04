package org.svgroz.chiwawa.fsm.core;

import org.svgroz.chiwawa.fsm.api.FSM;
import org.svgroz.chiwawa.fsm.api.GetState;
import org.svgroz.chiwawa.fsm.api.Transition;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Simon Grozovsky svgroz@outlook.com
 */
public class FSMDefault<T> implements FSM<T> {
    private record TransitionGuardMethod(MethodHandle guard, MethodHandle transition) {
    }

    private final Map<String, MethodHandle> getStates;
    private final Map<String, List<TransitionGuardMethod>> transitions;

    public FSMDefault(
            Class<T> target,
            Object annotatedFSM
    ) {
        getStates = getStateMethodHandles(target);
        transitions = getTransitions(annotatedFSM);
    }

    private Map<String, MethodHandle> getStateMethodHandles(Class<T> source) {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        final Map<String, MethodHandle> getStates = new HashMap<>();
        for (final var method : source.getMethods()) {
            final var methodAnnotation = method.getAnnotation(GetState.class);
            if (methodAnnotation == null) {
                continue;
            }

            if (method.getParameterCount() > 0) {
                throw new IllegalArgumentException(method + " has to not have args");
            }

            final MethodHandle getStateMethodHandle;
            try {
                getStateMethodHandle = lookup
                        .findVirtual(
                                source,
                                method.getName(),
                                MethodType.methodType(
                                        method.getReturnType()
                                )
                        );
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }

            var stateName = methodAnnotation.value();
            if (getStates.put(stateName, getStateMethodHandle) != null) {
                throw new IllegalArgumentException(source + " contains more than one " + GetState.class + " with value: " + stateName);
            }
        }

        if (getStates.isEmpty()) {
            throw new IllegalArgumentException(source + " does not contains method annotated with " + GetState.class);
        }

        return Map.copyOf(getStates);
    }

    private Map<String, List<TransitionGuardMethod>> getTransitions(Object annotatedFSM) {
        final var annotatedFSMClass = annotatedFSM.getClass();
        final var lookup = MethodHandles.lookup();
        final var transitions = new HashMap<String, List<TransitionGuardMethod>>();
        for (final var method : annotatedFSMClass.getMethods()) {
            var transitionAnnotation = method.getAnnotation(Transition.class);
            if (transitionAnnotation == null) {
                continue;
            }

            if (!method.getReturnType().isNestmateOf(boolean.class)) {
                throw new IllegalArgumentException(method + " has to have boolean return type");
            }

            final var parameters = method.getParameters();
            if (parameters.length != 2) {
                throw new IllegalArgumentException(method + " should have 2 args");
            }

            // TODO method args validation

            var transactionAnnotationValue = transitionAnnotation.value();
            Method targetMethod = null;
            for (final var annotatedFSMMethod : annotatedFSMClass.getMethods()) {
                if (method == annotatedFSMMethod) {
                    // TODO
                }

                if (transactionAnnotationValue.equals(annotatedFSMMethod.getName())) {
                    targetMethod = annotatedFSMMethod;
                    break;
                }
            }

            if (targetMethod == null) {
                throw new IllegalArgumentException(annotatedFSMClass + " does not contains method " + transactionAnnotationValue);
            }

            try {
                transitions
                        .computeIfAbsent(
                                transitionAnnotation.context(),
                                x -> new ArrayList<>()
                        )
                        .add(
                                new TransitionGuardMethod(
                                        lookup
                                                .findVirtual(
                                                        annotatedFSMClass,
                                                        method.getName(),
                                                        MethodType.methodType(
                                                                method.getReturnType(),
                                                                method.getParameterTypes()
                                                        )
                                                )
                                                .bindTo(
                                                        annotatedFSM
                                                ),
                                        lookup
                                                .findVirtual(
                                                        annotatedFSMClass,
                                                        targetMethod.getName(),
                                                        MethodType.methodType(
                                                                targetMethod.getReturnType(),
                                                                targetMethod.getParameterTypes()
                                                        )
                                                )
                                                .bindTo(
                                                        annotatedFSM
                                                )
                                )
                        );
            } catch (NoSuchMethodException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }

        return transitions;
    }

    @Override
    public T transit(final T target, final Object transition, final Object... context) {
        for (var ctxMethodHandleEntry : getStates.entrySet()) {
            final Object state;
            try {
                state = ctxMethodHandleEntry.getValue().invoke(target);
            } catch (Throwable ex) {
                throw new RuntimeException(ex);
            }

            for (var transitionGuardMethod : transitions.getOrDefault(ctxMethodHandleEntry.getKey(), Collections.emptyList())) {
                final boolean transit;
                try {
                    transit = (boolean) transitionGuardMethod.guard.invoke(state, transition);
                } catch (Throwable ex) {
                    throw new RuntimeException(ex);
                }

                if (transit) {
                    var args = prepareTransitionArgs(target, transition, context);
                    try {
                        transitionGuardMethod.transition.invokeWithArguments(args);
                    } catch (Throwable ex) {
                        throw new RuntimeException(ex);
                    }
                    return target;
                }
            }
        }

        return null;
    }

    private Object[] prepareTransitionArgs(Object target, Object transition, Object... context) {
        if (context == null || context.length == 0) {
            return new Object[]{target, transition};
        }

        var args = new Object[2 + context.length];
        args[0] = target;
        args[1] = transition;
        System.arraycopy(context, 0, args, 2, context.length);

        return args;
    }
}
