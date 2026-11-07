package dev.restockmanager.core;

public record Alert(Type type, TrackedRule rule, int quantity, String message) {
    public enum Type { HOTBAR_RESTOCK, LOW_KIT, RESTOCKED }
}
