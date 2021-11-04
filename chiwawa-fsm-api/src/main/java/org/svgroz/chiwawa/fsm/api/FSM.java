package org.svgroz.chiwawa.fsm.api;

/**
 * @author Simon Grozovsky svgroz@outlook.com
 */
public interface FSM<T> {
    T transit(T target, Object transition, Object... context);
}
