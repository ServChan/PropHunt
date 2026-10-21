package org.lts.prophunt.logic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

class HitPickerTest {

    private static final Vector EYE = new Vector(0.5, 65.6, 0.5);
    private static final Vector NORTH = new Vector(0, 0, -1);

    @Test
    void hitsTheNearestBlockInFront() {
        List<HitPicker.Target<String>> targets = List.of(
                new HitPicker.Target<>("far", HitPicker.cell(0, 65, -4)),
                new HitPicker.Target<>("near", HitPicker.cell(0, 65, -2)));
        Optional<HitPicker.Hit<String>> hit = HitPicker.pick(EYE, NORTH, 4.0, Double.MAX_VALUE, targets);
        assertEquals("near", hit.orElseThrow().owner());
        assertEquals(1.5, hit.get().distance(), 1.0E-6);
    }

    @Test
    void wallsBlockTheSwing() {
        List<HitPicker.Target<String>> targets = List.of(new HitPicker.Target<>("prop", HitPicker.cell(0, 65, -3)));
        assertTrue(HitPicker.pick(EYE, NORTH, 4.0, 1.5, targets).isEmpty());
        assertTrue(HitPicker.pick(EYE, NORTH, 4.0, 2.6, targets).isPresent());
    }

    @Test
    void reachLimitsTheSwing() {
        List<HitPicker.Target<String>> targets = List.of(new HitPicker.Target<>("prop", HitPicker.cell(0, 65, -6)));
        assertTrue(HitPicker.pick(EYE, NORTH, 4.0, Double.MAX_VALUE, targets).isEmpty());
    }

    @Test
    void missesBlocksBesideTheRay() {
        List<HitPicker.Target<String>> targets = List.of(new HitPicker.Target<>("prop", HitPicker.cell(2, 65, -2)));
        assertTrue(HitPicker.pick(EYE, NORTH, 4.0, Double.MAX_VALUE, targets).isEmpty());
    }

    @Test
    void movingPropUsesABlockAroundItsFeet() {
        List<HitPicker.Target<String>> targets = List.of(new HitPicker.Target<>("prop", HitPicker.around(0.5, 64, -1.5)));
        Vector down = new Vector(0, -0.5, -1);
        assertTrue(HitPicker.pick(EYE, down, 4.0, Double.MAX_VALUE, targets).isPresent());
        assertTrue(HitPicker.pick(EYE, NORTH, 4.0, Double.MAX_VALUE, targets).isEmpty());
    }

    @Test
    void standingInsideAPropCountsAsAHit() {
        List<HitPicker.Target<String>> targets = List.of(new HitPicker.Target<>("prop", HitPicker.cell(0, 65, 0)));
        assertEquals(0, HitPicker.pick(EYE, NORTH, 4.0, 0.2, targets).orElseThrow().distance());
    }
}
