package com.hydro.plsbl.simulator;

/**
 * Kommando für den Kran-Simulator
 */
public class CraneSimulatorCommand {

    private boolean abort;

    // Abholposition
    private int pickupX;
    private int pickupY;
    private int pickupZ;

    // Ablageposition
    private int releaseX;
    private int releaseY;
    private int releaseZ;

    // Barren-Daten
    private int length;
    private int width;
    private int thickness;
    private int weight;
    private boolean longIngot;
    private boolean rotate;

    // Stockyard IDs für die Datenbank-Aktualisierung
    private Long fromStockyardId;
    private Long toStockyardId;

    // === Builder Pattern ===

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final CraneSimulatorCommand cmd = new CraneSimulatorCommand();

        public Builder abort() {
            cmd.abort = true;
            return this;
        }

        public Builder pickup(int x, int y, int z) {
            cmd.pickupX = x;
            cmd.pickupY = y;
            cmd.pickupZ = z;
            return this;
        }

        public Builder release(int x, int y, int z) {
            cmd.releaseX = x;
            cmd.releaseY = y;
            cmd.releaseZ = z;
            return this;
        }

        public Builder ingot(int length, int width, int thickness, int weight) {
            cmd.length = length;
            cmd.width = width;
            cmd.thickness = thickness;
            cmd.weight = weight;
            return this;
        }

        public Builder longIngot(boolean longIngot) {
            cmd.longIngot = longIngot;
            return this;
        }

        public Builder rotate(boolean rotate) {
            cmd.rotate = rotate;
            return this;
        }

        public Builder fromStockyard(Long id) {
            cmd.fromStockyardId = id;
            return this;
        }

        public Builder toStockyard(Long id) {
            cmd.toStockyardId = id;
            return this;
        }

        public CraneSimulatorCommand build() {
            return cmd;
        }
    }

    // === Getters ===

    public boolean isAbort() {
        return abort;
    }

    public int getPickupX() {
        return pickupX;
    }

    public int getPickupY() {
        return pickupY;
    }

    public int getPickupZ() {
        return pickupZ;
    }

    public int getReleaseX() {
        return releaseX;
    }

    public int getReleaseY() {
        return releaseY;
    }

    public int getReleaseZ() {
        return releaseZ;
    }

    public int getLength() {
        return length;
    }

    public int getWidth() {
        return width;
    }

    public int getThickness() {
        return thickness;
    }

    public int getWeight() {
        return weight;
    }

    public boolean isLongIngot() {
        return longIngot;
    }

    public boolean isRotate() {
        return rotate;
    }

    public Long getFromStockyardId() {
        return fromStockyardId;
    }

    public Long getToStockyardId() {
        return toStockyardId;
    }

    @Override
    public String toString() {
        if (abort) {
            return "ABORT";
        }
        return String.format("Move from (%d,%d,%d) to (%d,%d,%d) - Ingot: %dx%dx%d, %dkg",
                pickupX, pickupY, pickupZ, releaseX, releaseY, releaseZ,
                length, width, thickness, weight);
    }
}
