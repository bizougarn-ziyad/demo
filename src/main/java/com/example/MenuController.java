package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.geometry.Pos;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.List;

public class MenuController {

    @FXML
    private FlowPane menuContainer;

    @FXML
    private Label statusLabel;

    @FXML
    public void initialize() {
        statusLabel.setText("Browse our menu and add items to your order");
        loadMenuFromDatabase();
    }

    private void loadMenuFromDatabase() {
        menuContainer.getChildren().clear();
        List<MenuItem> menuItems = DatabaseManager.getAllMenuItems();

        for (MenuItem item : menuItems) {
            if (item.isAvailable()) {
                VBox card = createMenuCard(item);
                menuContainer.getChildren().add(card);
            }
        }
    }

    private VBox createMenuCard(MenuItem item) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.CENTER);
        card.setStyle("-fx-background-color: white; -fx-padding: 15; -fx-border-color: #ddd; " +
                "-fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        card.setPrefWidth(200);
        card.setPrefHeight(250);

        // Add image
        ImageView imageView = new ImageView();
        imageView.setFitWidth(150);
        imageView.setFitHeight(150);
        imageView.setPreserveRatio(false); // Force all images to be the same size

        if (item.getImageData() != null && item.getImageData().length > 0) {
            try {
                ByteArrayInputStream bis = new ByteArrayInputStream(item.getImageData());
                Image image = new Image(bis);
                imageView.setImage(image);
            } catch (Exception e) {
                setDefaultImage(imageView);
            }
        } else {
            setDefaultImage(imageView);
        }

        // Add item name
        Label nameLabel = new Label(item.getName());
        nameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        // Add price
        Label priceLabel = new Label(String.format("$%.2f", item.getPrice()));
        priceLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #27ae60;");

        // Add button
        Button addButton = new Button("Add to Order");
        addButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 8 20;");
        addButton.setOnAction(e -> showQuantityDialog(item.getName(), item.getPrice()));

        card.getChildren().addAll(imageView, nameLabel, priceLabel, addButton);
        return card;
    }

    private void setDefaultImage(ImageView imageView) {
        try {
            InputStream defaultStream = getClass().getResourceAsStream("images/default-food.png");
            if (defaultStream != null) {
                imageView.setImage(new Image(defaultStream));
            }
        } catch (Exception e) {
            // No default image available
        }
    }

    private void showQuantityDialog(String itemName, double price) {
        TextInputDialog dialog = new TextInputDialog("1");
        dialog.setTitle("Order " + itemName);
        dialog.setHeaderText("How many would you like to order?");
        dialog.setContentText("Quantity:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(quantityStr -> {
            try {
                int quantity = Integer.parseInt(quantityStr);
                if (quantity <= 0) {
                    showAlert("Invalid Quantity", "Please enter a positive number.", AlertType.ERROR);
                    return;
                }

                // Get current client info
                String clientName = App.getCurrentClientName();
                String clientNumber = App.getCurrentClientNumber();

                if (clientName == null || clientNumber == null) {
                    showAlert("Error", "Client information not found. Please log in again.", AlertType.ERROR);
                    return;
                }

                // Add order to database
                boolean success = DatabaseManager.addOrder(
                        clientName,
                        clientNumber,
                        itemName,
                        quantity,
                        price,
                        Order.getCurrentTimestamp());

                if (success) {
                    double totalPrice = quantity * price;
                    statusLabel.setText(String.format("Order placed: %d x %s ($%.2f)", quantity, itemName, totalPrice));
                    statusLabel.setStyle("-fx-text-fill: #27ae60;");
                    showAlert("Order Confirmed",
                            String.format("Successfully ordered %d x %s\nTotal: $%.2f", quantity, itemName, totalPrice),
                            AlertType.INFORMATION);
                } else {
                    statusLabel.setText("Failed to place order. Please try again.");
                    statusLabel.setStyle("-fx-text-fill: #dc3545;");
                    showAlert("Order Failed", "Could not place your order. Please try again.", AlertType.ERROR);
                }

            } catch (NumberFormatException e) {
                showAlert("Invalid Input", "Please enter a valid number.", AlertType.ERROR);
            }
        });
    }

    private void showAlert(String title, String content, AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    @FXML
    private void handleBack() {
        try {
            FXMLLoader loader = App.setRootWithController("client");
            ClientController controller = loader.getController();
            controller.setClientInfo(App.getCurrentClientName(), App.getCurrentClientNumber());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
