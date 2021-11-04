package org.svgroz.chiwawa.fsm.core;

import org.junit.jupiter.api.Test;
import org.svgroz.chiwawa.fsm.api.GetState;
import org.svgroz.chiwawa.fsm.api.Transition;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Grozovsky svgroz@outlook.com
 */
class FSMDefaultTest {
    public static class TestTarget1 {

    }

    public static class TestTarget2 {
        @GetState
        public String getState(int x) {
            return "foo";
        }
    }

    public static class TestTarget3 {
        @GetState
        public String getState() {
            return "foo";
        }
    }

    public static class TestTarget4 {
        @GetState("x")
        public String getState1() {
            return "foo";
        }

        @GetState("x")
        public String getState2() {
            return "foo";
        }
    }

    public static class TestTarget5 {
        @GetState("x")
        public String getState1() {
            return "foo";
        }

        @GetState("y")
        public String getState2() {
            return "foo";
        }
    }

    public static class TestTarget6 {
        private String state;

        @GetState
        public String getState() {
            return state;
        }

        public void setState(final String state) {
            this.state = state;
        }
    }

    public static class FSM {
        @Transition("transit")
        public boolean t1(String state, String transition) {
            return "bar".equals(state) && "foo".equalsIgnoreCase(transition);
        }

        public TestTarget6 transit(TestTarget6 target, String state, int x) {
            target.setState(state);
            return target;
        }
    }

    @Test
    public void doesNotContainsGetStateMethod() {
        assertThrows(IllegalArgumentException.class, () -> new FSMDefault<>(TestTarget1.class, new FSM()));
    }

    @Test
    public void getStateHasArguments() {
        assertThrows(IllegalArgumentException.class, () -> new FSMDefault<>(TestTarget2.class, new FSM()));
    }

    @Test
    public void hasOneGetState() {
        new FSMDefault<>(TestTarget3.class, new FSM());
    }

    @Test
    public void hasTwoGetStateWithSameValues() {
        assertThrows(IllegalArgumentException.class, () -> new FSMDefault<>(TestTarget4.class, new FSM()));
    }

    @Test
    public void hasTwoGetStateWithDifferentValues() {
        new FSMDefault<>(TestTarget5.class, new FSM());
    }

    @Test
    public void transitTest() {
        var fsm = new FSMDefault<>(TestTarget6.class, new FSM());


        TestTarget6 target = new TestTarget6();
        target.setState("bar");
        fsm.transit(target, "foo", 22);
    }
}
