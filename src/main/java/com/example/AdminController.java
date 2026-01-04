package com.example;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import java.io.IOException;

public class AdminController {

    @FXML
    private Label adminCodeLabel;

    @FXML
    private Label statusLabel;

    private String adminCode;

    // Set admin information when navigating to this page
    public void setAdminInfo(String code) {
        this.adminCode = code;
        adminCodeLabel.setText("Admin: " + code);
    }

    @FXML
    private void handleLogout() {
        try {
            App.setRoot("main");
        } catch (IOException e) {
            statusLabel.setText("Error logging out");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleManageClients() {
        try {
            App.setRoot("admin_clients");
        } catch (IOException e) {
            statusLabel.setText("Error opening Manage Clients");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleManageOrders() {
        try {
            App.setRoot("admin_orders");
        } catch (IOException e) {
            statusLabel.setText("Error opening Manage Orders");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleManageMenu() {
        try {
            App.setRoot("admin_menu");
        } catch (IOException e) {
            statusLabel.setText("Error opening Manage Menu");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            e.printStackTrace();
        }
    }

    @FXML
    private void handleViewReports() {
        try {
            App.setRoot("admin_reports");
        } catch (IOException e) {
            statusLabel.setText("Error opening View Reports");
            statusLabel.setStyle("-fx-text-fill: #dc3545;");
            e.printStackTrace();
        }
    }
}
