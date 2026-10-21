package org.lts.prophunt.logic;

import java.util.List;
import java.util.Optional;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public final class HitPicker {

    public record Target<T>(T owner, BoundingBox box) {
    }

    public record Hit<T>(T owner, double distance) {
    }

    private HitPicker() {
    }

    public static <T> Optional<Hit<T>> pick(Vector eye, Vector direction, double reach, double wallDistance, List<Target<T>> targets) {
        Vector dir = direction.clone().normalize();
        double limit = Math.min(reach, wallDistance);
        Hit<T> best = null;
        for (Target<T> target : targets) {
            if (target.box().contains(eye)) {
                return Optional.of(new Hit<>(target.owner(), 0));
            }
            RayTraceResult result = target.box().rayTrace(eye, dir, reach);
            if (result == null) {
                continue;
            }
            double distance = result.getHitPosition().distance(eye);
            if (distance <= limit + 1.0E-4 && (best == null || distance < best.distance())) {
                best = new Hit<>(target.owner(), distance);
            }
        }
        return Optional.ofNullable(best);
    }

    public static BoundingBox cell(int x, int y, int z) {
        return new BoundingBox(x, y, z, x + 1, y + 1, z + 1);
    }

    public static BoundingBox around(double x, double y, double z) {
        return new BoundingBox(x - 0.5, y, z - 0.5, x + 0.5, y + 1, z + 0.5);
    }
}
