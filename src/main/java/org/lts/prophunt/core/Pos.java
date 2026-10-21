package org.lts.prophunt.core;

import java.util.Locale;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

public record Pos(double x, double y, double z, float yaw, float pitch) {

    public static Pos of(Location location) {
        return new Pos(round(location.getX()), round(location.getY()), round(location.getZ()),
                (float) round(location.getYaw()), (float) round(location.getPitch()));
    }

    public Location at(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public void write(ConfigurationSection section) {
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", (double) yaw);
        section.set("pitch", (double) pitch);
    }

    public static Pos read(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        return new Pos(section.getDouble("x"), section.getDouble("y"), section.getDouble("z"),
                (float) section.getDouble("yaw"), (float) section.getDouble("pitch"));
    }

    public String describe() {
        return String.format(Locale.ROOT,"%.1f %.1f %.1f", x, y, z);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
