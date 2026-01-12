package com.hydro.plsbl.plc.dto;

/**
 * Kommando-DTO fuer die SPS-Kommunikation (Schreiben zur SPS)
 *
 * Enthaelt alle Daten, die an die SPS gesendet werden:
 * - Aufnahme- und Ablageposition
 * - Barren-Dimensionen und Gewicht
 * - Steuerungsflags (Abbruch, Rotation, Langbarren)
 *
 * Immutable - verwendet Builder-Pattern.
 */
public final class PlcCommand {

    private final boolean abort;
    private final int pickupPositionX;
    private final int pickupPositionY;
    private final int pickupPositionZ;
    private final int releasePositionX;
    private final int releasePositionY;
    private final int releasePositionZ;
    private final int length;
    private final int width;
    private final int thickness;
    private final int weight;
    private final boolean longIngot;
    private final boolean rotate;

    private PlcCommand(Builder builder) {
        this.abort = builder.abort;
        this.pickupPositionX = builder.pickupPositionX;
        this.pickupPositionY = builder.pickupPositionY;
        this.pickupPositionZ = builder.pickupPositionZ;
        this.releasePositionX = builder.releasePositionX;
        this.releasePositionY = builder.releasePositionY;
        this.releasePositionZ = builder.releasePositionZ;
        this.length = builder.length;
        this.width = builder.width;
        this.thickness = builder.thickness;
        this.weight = builder.weight;
        this.longIngot = builder.longIngot;
        this.rotate = builder.rotate;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static PlcCommand abort() {
        return builder().abort(true).build();
    }

    // === Getters ===

    public boolean isAbort() {
        return abort;
    }

    public int getPickupPositionX() {
        return pickupPositionX;
    }

    public int getPickupPositionY() {
        return pickupPositionY;
    }

    public int getPickupPositionZ() {
        return pickupPositionZ;
    }

    public int getReleasePositionX() {
        return releasePositionX;
    }

    public int getReleasePositionY() {
        return releasePositionY;
    }

    public int getReleasePositionZ() {
        return releasePositionZ;
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

    @Override
    public String toString() {
        if (abort) {
            return "PlcCommand{ABORT}";
        }
        return "PlcCommand{" +
            "pickup=(" + pickupPositionX + "," + pickupPositionY + "," + pickupPositionZ + ")" +
            ", release=(" + releasePositionX + "," + releasePositionY + "," + releasePositionZ + ")" +
            ", size=" + length + "x" + width + "x" + thickness +
            ", weight=" + weight + "kg" +
            (longIngot ? ", LONG" : "") +
            (rotate ? ", ROTATE" : "") +
            '}';
    }

    // === Builder ===

    public static class Builder {
        private boolean abort = false;
        private int pickupPositionX = 0;
        private int pickupPositionY = 0;
        private int pickupPositionZ = 0;
        private int releasePositionX = 0;
        private int releasePositionY = 0;
        private int releasePositionZ = 0;
        private int length = 0;
        private int width = 0;
        private int thickness = 0;
        private int weight = 0;
        private boolean longIngot = false;
        private boolean rotate = false;

        public Builder abort(boolean abort) {
            this.abort = abort;
            return this;
        }

        public Builder pickupPositionX(int x) {
            this.pickupPositionX = x;
            return this;
        }

        public Builder pickupPositionY(int y) {
            this.pickupPositionY = y;
            return this;
        }

        public Builder pickupPositionZ(int z) {
            this.pickupPositionZ = z;
            return this;
        }

        public Builder pickupPosition(int x, int y, int z) {
            this.pickupPositionX = x;
            this.pickupPositionY = y;
            this.pickupPositionZ = z;
            return this;
        }

        public Builder releasePositionX(int x) {
            this.releasePositionX = x;
            return this;
        }

        public Builder releasePositionY(int y) {
            this.releasePositionY = y;
            return this;
        }

        public Builder releasePositionZ(int z) {
            this.releasePositionZ = z;
            return this;
        }

        public Builder releasePosition(int x, int y, int z) {
            this.releasePositionX = x;
            this.releasePositionY = y;
            this.releasePositionZ = z;
            return this;
        }

        public Builder length(int length) {
            this.length = length;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Builder thickness(int thickness) {
            this.thickness = thickness;
            return this;
        }

        public Builder dimensions(int length, int width, int thickness) {
            this.length = length;
            this.width = width;
            this.thickness = thickness;
            return this;
        }

        public Builder weight(int weight) {
            this.weight = weight;
            return this;
        }

        public Builder longIngot(boolean longIngot) {
            this.longIngot = longIngot;
            return this;
        }

        public Builder rotate(boolean rotate) {
            this.rotate = rotate;
            return this;
        }

        public PlcCommand build() {
            return new PlcCommand(this);
        }
    }
}
