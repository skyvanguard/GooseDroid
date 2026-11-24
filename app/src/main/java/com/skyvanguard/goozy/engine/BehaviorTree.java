package com.skyvanguard.goozy.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * Behavior Tree system for intelligent AI decision making.
 * Based on standard game AI behavior tree patterns.
 */
public class BehaviorTree {

    // ============== NODE STATUS ==============

    /**
     * Result of executing a node.
     */
    public enum Status {
        SUCCESS,    // Node completed successfully
        FAILURE,    // Node failed
        RUNNING     // Node is still executing
    }

    // ============== BASE NODE ==============

    /**
     * Base class for all behavior tree nodes.
     */
    public static abstract class Node {
        protected String name;
        protected Status lastStatus = Status.FAILURE;

        public Node(String name) {
            this.name = name;
        }

        public abstract Status execute(Blackboard blackboard);

        public String getName() { return name; }
        public Status getLastStatus() { return lastStatus; }

        public void reset() {
            lastStatus = Status.FAILURE;
        }
    }

    // ============== COMPOSITE NODES ==============

    /**
     * Composite node - has children.
     */
    public static abstract class CompositeNode extends Node {
        protected List<Node> children = new ArrayList<>();

        public CompositeNode(String name) {
            super(name);
        }

        public CompositeNode addChild(Node child) {
            children.add(child);
            return this;
        }

        public CompositeNode addChildren(Node... nodes) {
            for (Node node : nodes) {
                children.add(node);
            }
            return this;
        }

        @Override
        public void reset() {
            super.reset();
            for (Node child : children) {
                child.reset();
            }
        }
    }

    /**
     * Selector node (OR logic).
     * Tries each child until one succeeds.
     * Returns SUCCESS if any child succeeds.
     * Returns FAILURE only if all children fail.
     */
    public static class Selector extends CompositeNode {
        private int currentChild = 0;

        public Selector(String name) {
            super(name);
        }

        @Override
        public Status execute(Blackboard blackboard) {
            while (currentChild < children.size()) {
                Status status = children.get(currentChild).execute(blackboard);

                if (status == Status.SUCCESS) {
                    currentChild = 0;
                    lastStatus = Status.SUCCESS;
                    return Status.SUCCESS;
                }

                if (status == Status.RUNNING) {
                    lastStatus = Status.RUNNING;
                    return Status.RUNNING;
                }

                // FAILURE - try next child
                currentChild++;
            }

            currentChild = 0;
            lastStatus = Status.FAILURE;
            return Status.FAILURE;
        }

        @Override
        public void reset() {
            super.reset();
            currentChild = 0;
        }
    }

    /**
     * Sequence node (AND logic).
     * Executes children in order until one fails.
     * Returns SUCCESS only if all children succeed.
     * Returns FAILURE if any child fails.
     */
    public static class Sequence extends CompositeNode {
        private int currentChild = 0;

        public Sequence(String name) {
            super(name);
        }

        @Override
        public Status execute(Blackboard blackboard) {
            while (currentChild < children.size()) {
                Status status = children.get(currentChild).execute(blackboard);

                if (status == Status.FAILURE) {
                    currentChild = 0;
                    lastStatus = Status.FAILURE;
                    return Status.FAILURE;
                }

                if (status == Status.RUNNING) {
                    lastStatus = Status.RUNNING;
                    return Status.RUNNING;
                }

                // SUCCESS - try next child
                currentChild++;
            }

            currentChild = 0;
            lastStatus = Status.SUCCESS;
            return Status.SUCCESS;
        }

        @Override
        public void reset() {
            super.reset();
            currentChild = 0;
        }
    }

    /**
     * Random Selector - picks a random child to execute.
     */
    public static class RandomSelector extends CompositeNode {
        private int selectedChild = -1;

        public RandomSelector(String name) {
            super(name);
        }

        @Override
        public Status execute(Blackboard blackboard) {
            if (children.isEmpty()) {
                lastStatus = Status.FAILURE;
                return Status.FAILURE;
            }

            if (selectedChild < 0) {
                selectedChild = (int)(Math.random() * children.size());
            }

            Status status = children.get(selectedChild).execute(blackboard);

            if (status != Status.RUNNING) {
                selectedChild = -1;
            }

            lastStatus = status;
            return status;
        }

        @Override
        public void reset() {
            super.reset();
            selectedChild = -1;
        }
    }

    /**
     * Parallel node - executes all children simultaneously.
     * Succeeds if required number of children succeed.
     */
    public static class Parallel extends CompositeNode {
        private int requiredSuccesses;

        public Parallel(String name, int requiredSuccesses) {
            super(name);
            this.requiredSuccesses = requiredSuccesses;
        }

        @Override
        public Status execute(Blackboard blackboard) {
            int successes = 0;
            int failures = 0;
            boolean hasRunning = false;

            for (Node child : children) {
                Status status = child.execute(blackboard);
                if (status == Status.SUCCESS) successes++;
                else if (status == Status.FAILURE) failures++;
                else hasRunning = true;
            }

            if (successes >= requiredSuccesses) {
                lastStatus = Status.SUCCESS;
                return Status.SUCCESS;
            }

            if (failures > children.size() - requiredSuccesses) {
                lastStatus = Status.FAILURE;
                return Status.FAILURE;
            }

            lastStatus = hasRunning ? Status.RUNNING : Status.FAILURE;
            return lastStatus;
        }
    }

    // ============== DECORATOR NODES ==============

    /**
     * Decorator node - wraps a single child.
     */
    public static abstract class DecoratorNode extends Node {
        protected Node child;

        public DecoratorNode(String name, Node child) {
            super(name);
            this.child = child;
        }

        @Override
        public void reset() {
            super.reset();
            if (child != null) child.reset();
        }
    }

    /**
     * Inverter - inverts the result of child.
     */
    public static class Inverter extends DecoratorNode {
        public Inverter(Node child) {
            super("Inverter", child);
        }

        @Override
        public Status execute(Blackboard blackboard) {
            Status status = child.execute(blackboard);
            if (status == Status.SUCCESS) {
                lastStatus = Status.FAILURE;
            } else if (status == Status.FAILURE) {
                lastStatus = Status.SUCCESS;
            } else {
                lastStatus = Status.RUNNING;
            }
            return lastStatus;
        }
    }

    /**
     * Succeeder - always returns SUCCESS.
     */
    public static class Succeeder extends DecoratorNode {
        public Succeeder(Node child) {
            super("Succeeder", child);
        }

        @Override
        public Status execute(Blackboard blackboard) {
            child.execute(blackboard);
            lastStatus = Status.SUCCESS;
            return Status.SUCCESS;
        }
    }

    /**
     * Repeater - repeats child N times or until failure.
     */
    public static class Repeater extends DecoratorNode {
        private int times;
        private int count = 0;

        public Repeater(Node child, int times) {
            super("Repeater", child);
            this.times = times;
        }

        @Override
        public Status execute(Blackboard blackboard) {
            while (count < times) {
                Status status = child.execute(blackboard);
                if (status == Status.FAILURE) {
                    count = 0;
                    lastStatus = Status.FAILURE;
                    return Status.FAILURE;
                }
                if (status == Status.RUNNING) {
                    lastStatus = Status.RUNNING;
                    return Status.RUNNING;
                }
                count++;
            }
            count = 0;
            lastStatus = Status.SUCCESS;
            return Status.SUCCESS;
        }

        @Override
        public void reset() {
            super.reset();
            count = 0;
        }
    }

    /**
     * Cooldown - prevents re-execution for a duration.
     */
    public static class Cooldown extends DecoratorNode {
        private float cooldownTime;
        private double lastExecutionTime = -999f;

        public Cooldown(Node child, float cooldownSeconds) {
            super("Cooldown", child);
            this.cooldownTime = cooldownSeconds;
        }

        @Override
        public Status execute(Blackboard blackboard) {
            double currentTime = blackboard.getDouble("time", 0.0);

            if (currentTime - lastExecutionTime < cooldownTime) {
                lastStatus = Status.FAILURE;
                return Status.FAILURE;
            }

            Status status = child.execute(blackboard);
            if (status == Status.SUCCESS) {
                lastExecutionTime = currentTime;
            }
            lastStatus = status;
            return status;
        }
    }

    // ============== CONDITION NODES ==============

    /**
     * Condition node - checks a condition.
     */
    public static abstract class ConditionNode extends Node {
        public ConditionNode(String name) {
            super(name);
        }

        public abstract boolean check(Blackboard blackboard);

        @Override
        public Status execute(Blackboard blackboard) {
            lastStatus = check(blackboard) ? Status.SUCCESS : Status.FAILURE;
            return lastStatus;
        }
    }

    /**
     * Lambda condition - uses a functional interface.
     */
    public static class LambdaCondition extends ConditionNode {
        private Condition condition;

        public interface Condition {
            boolean check(Blackboard blackboard);
        }

        public LambdaCondition(String name, Condition condition) {
            super(name);
            this.condition = condition;
        }

        @Override
        public boolean check(Blackboard blackboard) {
            return condition.check(blackboard);
        }
    }

    // ============== ACTION NODES ==============

    /**
     * Action node - performs an action.
     */
    public static abstract class ActionNode extends Node {
        public ActionNode(String name) {
            super(name);
        }
    }

    /**
     * Lambda action - uses a functional interface.
     */
    public static class LambdaAction extends ActionNode {
        private Action action;

        public interface Action {
            Status execute(Blackboard blackboard);
        }

        public LambdaAction(String name, Action action) {
            super(name);
            this.action = action;
        }

        @Override
        public Status execute(Blackboard blackboard) {
            lastStatus = action.execute(blackboard);
            return lastStatus;
        }
    }

    /**
     * Wait action - waits for a duration.
     */
    public static class WaitAction extends ActionNode {
        private float duration;
        private double startTime = -1f;

        public WaitAction(float seconds) {
            super("Wait");
            this.duration = seconds;
        }

        @Override
        public Status execute(Blackboard blackboard) {
            double currentTime = blackboard.getDouble("time", 0.0);

            if (startTime < 0) {
                startTime = currentTime;
            }

            if (currentTime - startTime >= duration) {
                startTime = -1f;
                lastStatus = Status.SUCCESS;
                return Status.SUCCESS;
            }

            lastStatus = Status.RUNNING;
            return Status.RUNNING;
        }

        @Override
        public void reset() {
            super.reset();
            startTime = -1f;
        }
    }

    // ============== BLACKBOARD ==============

    /**
     * Blackboard - shared data store for the tree.
     */
    public static class Blackboard {
        private java.util.HashMap<String, Object> data = new java.util.HashMap<>();

        public void set(String key, Object value) {
            data.put(key, value);
        }

        public Object get(String key) {
            return data.get(key);
        }

        public Object get(String key, Object defaultValue) {
            return data.containsKey(key) ? data.get(key) : defaultValue;
        }

        public float getFloat(String key, float defaultValue) {
            Object val = data.get(key);
            if (val instanceof Number) {
                return ((Number) val).floatValue();
            }
            return defaultValue;
        }

        public double getDouble(String key, double defaultValue) {
            Object val = data.get(key);
            if (val instanceof Number) {
                return ((Number) val).doubleValue();
            }
            return defaultValue;
        }

        public int getInt(String key, int defaultValue) {
            Object val = data.get(key);
            if (val instanceof Number) {
                return ((Number) val).intValue();
            }
            return defaultValue;
        }

        public boolean getBool(String key, boolean defaultValue) {
            Object val = data.get(key);
            if (val instanceof Boolean) {
                return (Boolean) val;
            }
            return defaultValue;
        }

        public String getString(String key, String defaultValue) {
            Object val = data.get(key);
            if (val instanceof String) {
                return (String) val;
            }
            return defaultValue;
        }

        public boolean has(String key) {
            return data.containsKey(key);
        }

        public void remove(String key) {
            data.remove(key);
        }

        public void clear() {
            data.clear();
        }
    }

    // ============== TREE ==============

    private Node root;
    private Blackboard blackboard;

    public BehaviorTree(Node root) {
        this.root = root;
        this.blackboard = new Blackboard();
    }

    public BehaviorTree(Node root, Blackboard blackboard) {
        this.root = root;
        this.blackboard = blackboard;
    }

    public Status tick() {
        if (root == null) return Status.FAILURE;
        return root.execute(blackboard);
    }

    public Blackboard getBlackboard() {
        return blackboard;
    }

    public void reset() {
        if (root != null) root.reset();
    }

    public Node getRoot() {
        return root;
    }
}
