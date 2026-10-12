package com.skyvanguard.goozy;

import com.skyvanguard.goozy.engine.BehaviorTree;
import com.skyvanguard.goozy.engine.BehaviorTree.*;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class BehaviorTreeTest {

    private Blackboard blackboard;

    @Before
    public void setUp() {
        blackboard = new Blackboard();
    }

    // ============== BLACKBOARD ==============

    @Test
    public void blackboard_setAndGet() {
        blackboard.set("key", "value");
        assertEquals("value", blackboard.get("key"));
    }

    @Test
    public void blackboard_getWithDefault() {
        assertEquals("default", blackboard.get("missing", "default"));
    }

    @Test
    public void blackboard_getFloat() {
        blackboard.set("score", 42.5f);
        assertEquals(42.5f, blackboard.getFloat("score", 0f), 0.01f);
    }

    @Test
    public void blackboard_getFloatDefault() {
        assertEquals(99f, blackboard.getFloat("missing", 99f), 0.01f);
    }

    @Test
    public void blackboard_getInt() {
        blackboard.set("count", 7);
        assertEquals(7, blackboard.getInt("count", 0));
    }

    @Test
    public void blackboard_getBool() {
        blackboard.set("flag", true);
        assertTrue(blackboard.getBool("flag", false));
    }

    @Test
    public void blackboard_getBoolDefault() {
        assertFalse(blackboard.getBool("missing", false));
    }

    @Test
    public void blackboard_getString() {
        blackboard.set("name", "goose");
        assertEquals("goose", blackboard.getString("name", ""));
    }

    @Test
    public void blackboard_has() {
        blackboard.set("exists", 1);
        assertTrue(blackboard.has("exists"));
        assertFalse(blackboard.has("nope"));
    }

    @Test
    public void blackboard_remove() {
        blackboard.set("key", "value");
        blackboard.remove("key");
        assertFalse(blackboard.has("key"));
    }

    @Test
    public void blackboard_clear() {
        blackboard.set("a", 1);
        blackboard.set("b", 2);
        blackboard.clear();
        assertFalse(blackboard.has("a"));
        assertFalse(blackboard.has("b"));
    }

    // ============== LAMBDA CONDITION ==============

    @Test
    public void lambdaCondition_successWhenTrue() {
        LambdaCondition cond = new LambdaCondition("AlwaysTrue", bb -> true);
        assertEquals(Status.SUCCESS, cond.execute(blackboard));
    }

    @Test
    public void lambdaCondition_failureWhenFalse() {
        LambdaCondition cond = new LambdaCondition("AlwaysFalse", bb -> false);
        assertEquals(Status.FAILURE, cond.execute(blackboard));
    }

    @Test
    public void lambdaCondition_readsBlackboard() {
        blackboard.set("health", 50f);
        LambdaCondition cond = new LambdaCondition("HealthCheck",
            bb -> bb.getFloat("health", 0f) > 30f);
        assertEquals(Status.SUCCESS, cond.execute(blackboard));
    }

    // ============== LAMBDA ACTION ==============

    @Test
    public void lambdaAction_returnsSuccess() {
        LambdaAction action = new LambdaAction("DoSomething", bb -> Status.SUCCESS);
        assertEquals(Status.SUCCESS, action.execute(blackboard));
    }

    @Test
    public void lambdaAction_returnsFailure() {
        LambdaAction action = new LambdaAction("FailAction", bb -> Status.FAILURE);
        assertEquals(Status.FAILURE, action.execute(blackboard));
    }

    @Test
    public void lambdaAction_returnsRunning() {
        LambdaAction action = new LambdaAction("LongAction", bb -> Status.RUNNING);
        assertEquals(Status.RUNNING, action.execute(blackboard));
    }

    @Test
    public void lambdaAction_modifiesBlackboard() {
        LambdaAction action = new LambdaAction("SetValue", bb -> {
            bb.set("result", 42);
            return Status.SUCCESS;
        });
        action.execute(blackboard);
        assertEquals(42, blackboard.getInt("result", 0));
    }

    // ============== SELECTOR ==============

    @Test
    public void selector_succeedsOnFirstSuccess() {
        Selector sel = new Selector("TestSelector");
        sel.addChild(new LambdaAction("Fail", bb -> Status.FAILURE));
        sel.addChild(new LambdaAction("Succeed", bb -> Status.SUCCESS));
        sel.addChild(new LambdaAction("NeverReached", bb -> Status.FAILURE));

        assertEquals(Status.SUCCESS, sel.execute(blackboard));
    }

    @Test
    public void selector_failsWhenAllFail() {
        Selector sel = new Selector("AllFail");
        sel.addChild(new LambdaAction("Fail1", bb -> Status.FAILURE));
        sel.addChild(new LambdaAction("Fail2", bb -> Status.FAILURE));

        assertEquals(Status.FAILURE, sel.execute(blackboard));
    }

    @Test
    public void selector_returnsRunning() {
        Selector sel = new Selector("WithRunning");
        sel.addChild(new LambdaAction("Fail", bb -> Status.FAILURE));
        sel.addChild(new LambdaAction("Running", bb -> Status.RUNNING));

        assertEquals(Status.RUNNING, sel.execute(blackboard));
    }

    @Test
    public void selector_empty_returnsFAILURE() {
        Selector sel = new Selector("Empty");
        assertEquals(Status.FAILURE, sel.execute(blackboard));
    }

    // ============== SEQUENCE ==============

    @Test
    public void sequence_succeedsWhenAllSucceed() {
        Sequence seq = new Sequence("AllSucceed");
        seq.addChild(new LambdaAction("S1", bb -> Status.SUCCESS));
        seq.addChild(new LambdaAction("S2", bb -> Status.SUCCESS));
        seq.addChild(new LambdaAction("S3", bb -> Status.SUCCESS));

        assertEquals(Status.SUCCESS, seq.execute(blackboard));
    }

    @Test
    public void sequence_failsOnFirstFailure() {
        Sequence seq = new Sequence("FailMid");
        seq.addChild(new LambdaAction("S1", bb -> Status.SUCCESS));
        seq.addChild(new LambdaAction("Fail", bb -> Status.FAILURE));
        seq.addChild(new LambdaAction("NeverReached", bb -> Status.SUCCESS));

        assertEquals(Status.FAILURE, seq.execute(blackboard));
    }

    @Test
    public void sequence_returnsRunning() {
        Sequence seq = new Sequence("WithRunning");
        seq.addChild(new LambdaAction("S1", bb -> Status.SUCCESS));
        seq.addChild(new LambdaAction("Running", bb -> Status.RUNNING));

        assertEquals(Status.RUNNING, seq.execute(blackboard));
    }

    @Test
    public void sequence_empty_returnsSUCCESS() {
        Sequence seq = new Sequence("Empty");
        assertEquals(Status.SUCCESS, seq.execute(blackboard));
    }

    // ============== INVERTER ==============

    @Test
    public void inverter_invertsSuccess() {
        Inverter inv = new Inverter(new LambdaAction("S", bb -> Status.SUCCESS));
        assertEquals(Status.FAILURE, inv.execute(blackboard));
    }

    @Test
    public void inverter_invertsFailure() {
        Inverter inv = new Inverter(new LambdaAction("F", bb -> Status.FAILURE));
        assertEquals(Status.SUCCESS, inv.execute(blackboard));
    }

    @Test
    public void inverter_passesRunningThrough() {
        Inverter inv = new Inverter(new LambdaAction("R", bb -> Status.RUNNING));
        assertEquals(Status.RUNNING, inv.execute(blackboard));
    }

    // ============== SUCCEEDER ==============

    @Test
    public void succeeder_alwaysReturnsSuccess() {
        Succeeder s = new Succeeder(new LambdaAction("Fail", bb -> Status.FAILURE));
        assertEquals(Status.SUCCESS, s.execute(blackboard));
    }

    // ============== REPEATER ==============

    @Test
    public void repeater_executesNTimes() {
        final int[] count = {0};
        Repeater rep = new Repeater(
            new LambdaAction("Count", bb -> { count[0]++; return Status.SUCCESS; }),
            3
        );
        assertEquals(Status.SUCCESS, rep.execute(blackboard));
        assertEquals(3, count[0]);
    }

    @Test
    public void repeater_stopsOnFailure() {
        final int[] count = {0};
        Repeater rep = new Repeater(
            new LambdaAction("FailAt2", bb -> {
                count[0]++;
                return count[0] >= 2 ? Status.FAILURE : Status.SUCCESS;
            }),
            5
        );
        assertEquals(Status.FAILURE, rep.execute(blackboard));
        assertEquals(2, count[0]);
    }

    // ============== WAIT ACTION ==============

    @Test
    public void waitAction_returnsRunningBeforeDuration() {
        WaitAction wait = new WaitAction(5f);
        blackboard.set("time", 0f);
        assertEquals(Status.RUNNING, wait.execute(blackboard));
    }

    @Test
    public void waitAction_returnsSuccessAfterDuration() {
        WaitAction wait = new WaitAction(5f);
        blackboard.set("time", 0f);
        wait.execute(blackboard); // starts timer

        blackboard.set("time", 6f);
        assertEquals(Status.SUCCESS, wait.execute(blackboard));
    }

    // ============== COOLDOWN ==============

    @Test
    public void cooldown_allowsFirstExecution() {
        Cooldown cd = new Cooldown(
            new LambdaAction("Action", bb -> Status.SUCCESS),
            10f
        );
        blackboard.set("time", 0f);
        assertEquals(Status.SUCCESS, cd.execute(blackboard));
    }

    @Test
    public void cooldown_blocksReexecution() {
        Cooldown cd = new Cooldown(
            new LambdaAction("Action", bb -> Status.SUCCESS),
            10f
        );
        blackboard.set("time", 0f);
        cd.execute(blackboard); // first execution succeeds

        blackboard.set("time", 5f); // only 5s elapsed, need 10s
        assertEquals(Status.FAILURE, cd.execute(blackboard));
    }

    @Test
    public void cooldown_allowsAfterCooldownExpires() {
        Cooldown cd = new Cooldown(
            new LambdaAction("Action", bb -> Status.SUCCESS),
            10f
        );
        blackboard.set("time", 0f);
        cd.execute(blackboard);

        blackboard.set("time", 15f); // 15s > 10s cooldown
        assertEquals(Status.SUCCESS, cd.execute(blackboard));
    }

    // ============== PARALLEL ==============

    @Test
    public void parallel_succeedsWithEnoughSuccesses() {
        Parallel par = new Parallel("Par", 2);
        par.addChild(new LambdaAction("S1", bb -> Status.SUCCESS));
        par.addChild(new LambdaAction("S2", bb -> Status.SUCCESS));
        par.addChild(new LambdaAction("F1", bb -> Status.FAILURE));

        assertEquals(Status.SUCCESS, par.execute(blackboard));
    }

    @Test
    public void parallel_failsWithTooManyFailures() {
        Parallel par = new Parallel("Par", 2);
        par.addChild(new LambdaAction("S1", bb -> Status.SUCCESS));
        par.addChild(new LambdaAction("F1", bb -> Status.FAILURE));
        par.addChild(new LambdaAction("F2", bb -> Status.FAILURE));

        assertEquals(Status.FAILURE, par.execute(blackboard));
    }

    // ============== FULL TREE ==============

    @Test
    public void fullTree_executesCorrectBranch() {
        blackboard.set("hungry", true);

        BehaviorTree tree = new BehaviorTree(
            new Selector("Root")
                .addChild(new Sequence("EatBranch")
                    .addChild(new LambdaCondition("IsHungry", bb -> bb.getBool("hungry", false)))
                    .addChild(new LambdaAction("Eat", bb -> {
                        bb.set("ate", true);
                        return Status.SUCCESS;
                    })))
                .addChild(new LambdaAction("Wander", bb -> {
                    bb.set("wandered", true);
                    return Status.SUCCESS;
                })),
            blackboard
        );

        assertEquals(Status.SUCCESS, tree.tick());
        assertTrue(blackboard.getBool("ate", false));
        assertFalse(blackboard.getBool("wandered", false));
    }

    @Test
    public void fullTree_fallsToDefault() {
        blackboard.set("hungry", false);

        BehaviorTree tree = new BehaviorTree(
            new Selector("Root")
                .addChild(new Sequence("EatBranch")
                    .addChild(new LambdaCondition("IsHungry", bb -> bb.getBool("hungry", false)))
                    .addChild(new LambdaAction("Eat", bb -> {
                        bb.set("ate", true);
                        return Status.SUCCESS;
                    })))
                .addChild(new LambdaAction("Wander", bb -> {
                    bb.set("wandered", true);
                    return Status.SUCCESS;
                })),
            blackboard
        );

        assertEquals(Status.SUCCESS, tree.tick());
        assertFalse(blackboard.getBool("ate", false));
        assertTrue(blackboard.getBool("wandered", false));
    }

    @Test
    public void tree_reset_clearsState() {
        BehaviorTree tree = new BehaviorTree(
            new Selector("Root")
                .addChild(new LambdaAction("Act", bb -> Status.SUCCESS))
        );
        tree.tick();
        tree.reset();
        assertEquals(Status.FAILURE, tree.getRoot().getLastStatus());
    }
}
