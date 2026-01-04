package com.example;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;

/**
 * Represents a menu item with name, price, and availability status.
 */
public class MenuItem {
    private final SimpleIntegerProperty id;
    private final SimpleStringProperty name;
    private final SimpleDoubleProperty price;
    private final SimpleBooleanProperty available;
    private byte[] imageData;
    private String imageFileName; // For tracking the original file name

    public MenuItem(int id, String name, double price, boolean available, byte[] imageData) {
        this.id = new SimpleIntegerProperty(id);
        this.name = new SimpleStringProperty(name);
        this.price = new SimpleDoubleProperty(price);
        this.available = new SimpleBooleanProperty(available);
        this.imageData = imageData;
    }

    // For creating new items (id will be auto-generated)
    public MenuItem(String name, double price, boolean available, byte[] imageData) {
        this(0, name, price, available, imageData);
    }

    // Property getters for JavaFX TableView binding
    public SimpleIntegerProperty idProperty() {
        return id;
    }

    public SimpleStringProperty nameProperty() {
        return name;
    }

    public SimpleDoubleProperty priceProperty() {
        return price;
    }

    public SimpleBooleanProperty availableProperty() {
        return available;
    }

    // Regular getters
    public int getId() {
        return id.get();
    }

    public String getName() {
        return name.get();
    }

    public double getPrice() {
        return price.get();
    }

    public boolean isAvailable() {
        return available.get();
    }

    public byte[] getImageData() {
        return imageData;
    }

    // Regular setters
    public void setId(int id) {
        this.id.set(id);
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public void setPrice(double price) {
        this.price.set(price);
    }

    public void setAvailable(boolean available) {
        this.available.set(available);
    }

    public void setImageData(byte[] imageData) {
        this.imageData = imageData;
    }

    public String getImageFileName() {
        return imageFileName;
    }

    public void setImageFileName(String imageFileName) {
        this.imageFileName = imageFileName;
    }
}