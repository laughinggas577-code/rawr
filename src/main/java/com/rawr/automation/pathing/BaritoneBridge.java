package com.rawr.automation.pathing;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Reflection bridge for optional Baritone integration without hard dependency.
 */
public class BaritoneBridge {

    private boolean availabilityChecked;
    private boolean available;

    public boolean isAvailable() {
        if (availabilityChecked) {
            return available;
        }

        availabilityChecked = true;
        try {
            Class.forName("baritone.api.BaritoneAPI");
            Class.forName("baritone.api.pathing.goals.GoalBlock");
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
        return available;
    }

    public boolean startPath(int x, int y, int z) {
        if (!isAvailable()) return false;

        try {
            Object primary = getPrimaryBaritone();
            if (primary == null) return false;

            Class<?> goalClass = Class.forName("baritone.api.pathing.goals.GoalBlock");
            Constructor<?> ctor = goalClass.getConstructor(int.class, int.class, int.class);
            Object goal = ctor.newInstance(x, y, z);

            Object customGoalProcess = invoke(primary, "getCustomGoalProcess");
            if (customGoalProcess == null) return false;

            Method setGoalAndPath = customGoalProcess.getClass().getMethod("setGoalAndPath", Class.forName("baritone.api.pathing.goals.Goal"));
            setGoalAndPath.invoke(customGoalProcess, goal);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean isPathing() {
        if (!isAvailable()) return false;

        try {
            Object primary = getPrimaryBaritone();
            if (primary == null) return false;
            Object behavior = invoke(primary, "getPathingBehavior");
            if (behavior == null) return false;
            Object out = invoke(behavior, "isPathing");
            return out instanceof Boolean && (Boolean) out;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void cancel() {
        if (!isAvailable()) return;

        try {
            Object primary = getPrimaryBaritone();
            if (primary == null) return;
            Object behavior = invoke(primary, "getPathingBehavior");
            if (behavior == null) return;
            invoke(behavior, "cancelEverything");
        } catch (Throwable ignored) {
        }
    }

    private Object getPrimaryBaritone() throws Exception {
        Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
        Object provider = invokeStatic(apiClass, "getProvider");
        if (provider == null) return null;
        return invoke(provider, "getPrimaryBaritone");
    }

    private Object invoke(Object target, String method) throws Exception {
        Method m = target.getClass().getMethod(method);
        return m.invoke(target);
    }

    private Object invokeStatic(Class<?> type, String method) throws Exception {
        Method m = type.getMethod(method);
        return m.invoke(null);
    }
}
