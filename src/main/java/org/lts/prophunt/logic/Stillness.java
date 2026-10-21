package org.lts.prophunt.logic;

public final class Stillness {

    private final double toleranceSquared;
    private double x;
    private double y;
    private double z;
    private boolean anchored;
    private int ticks;

    public Stillness(double tolerance) {
        this.toleranceSquared = tolerance * tolerance;
    }

    public int update(double nx, double ny, double nz) {
        if (!anchored || moved(nx, ny, nz)) {
            reset(nx, ny, nz);
        } else {
            ticks++;
        }
        return ticks;
    }

    public boolean moved(double nx, double ny, double nz) {
        double dx = nx - x;
        double dy = ny - y;
        double dz = nz - z;
        return dx * dx + dy * dy + dz * dz > toleranceSquared;
    }

    public void reset(double nx, double ny, double nz) {
        x = nx;
        y = ny;
        z = nz;
        anchored = true;
        ticks = 0;
    }

    public int ticks() {
        return ticks;
    }
}
