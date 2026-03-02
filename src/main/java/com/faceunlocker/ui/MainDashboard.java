package com.faceunlocker.ui;

import com.faceunlocker.config.AppConfig;
import com.faceunlocker.config.ConfigManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;

/**
 * The main application dashboard with three tabs:
 * <ol>
 *   <li><b>Locked Apps</b> – add / remove executables.</li>
 *   <li><b>Face Registration</b> – enroll / clear face data.</li>
 *   <li><b>Access Log</b> – view recent unlock attempts.</li>
 * </ol>
 */
public class MainDashboard {

    private final ConfigManager configManager;
    private BorderPane root;

    public MainDashboard(ConfigManager configManager) {
        this.configManager = configManager;
        build();
    }

    /** @return the root {@link Parent} to embed in a {@link javafx.scene.Scene}. */
    public Parent getRoot() {
        return root;
    }

    // -----------------------------------------------------------------------
    // Build
    // -----------------------------------------------------------------------

    private void build() {
        root = new BorderPane();
        root.getStyleClass().add("dashboard-root");

        root.setTop(buildHeader());
        root.setCenter(buildTabPane());
    }

    private Node buildHeader() {
        HBox header = new HBox();
        header.getStyleClass().add("header");
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 16, 24));

        Text title = new Text("🔒  AI Face Unlocker");
        title.getStyleClass().add("header-title");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Text subtitle = new Text("Offline · Private · Secure");
        subtitle.getStyleClass().add("header-subtitle");

        header.getChildren().addAll(title, spacer, subtitle);
        return header;
    }

    private Node buildTabPane() {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("main-tabs");

        tabPane.getTabs().add(new Tab("🔒  Locked Apps",     buildLockedAppsTab()));
        tabPane.getTabs().add(new Tab("📷  Face Registration", buildFaceRegistrationTab()));
        tabPane.getTabs().add(new Tab("📋  Access Log",       buildAccessLogTab()));

        return tabPane;
    }

    // -----------------------------------------------------------------------
    // Tab 1 – Locked Apps
    // -----------------------------------------------------------------------

    private Node buildLockedAppsTab() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("tab-content");

        Label hint = new Label(
                "Add the executables you want to protect with face recognition.");
        hint.getStyleClass().add("hint-label");

        ObservableList<String> items = FXCollections.observableArrayList(
                configManager.getConfig().getLockedApps());

        ListView<String> listView = new ListView<>(items);
        listView.getStyleClass().add("app-list");
        listView.setPrefHeight(300);

        // Toolbar
        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button addBtn    = new Button("+ Add App");
        Button removeBtn = new Button("− Remove");
        Button clearBtn  = new Button("Clear All");

        addBtn.getStyleClass().add("btn-primary");
        removeBtn.getStyleClass().add("btn-danger");
        clearBtn.getStyleClass().add("btn-secondary");

        addBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select Executable");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Executables", "*.exe"));
            Stage stage = (Stage) content.getScene().getWindow();
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                String name = file.getName().toLowerCase();
                if (!items.contains(name)) {
                    items.add(name);
                    configManager.getConfig().getLockedApps().add(name);
                    configManager.save();
                }
            }
        });

        removeBtn.setOnAction(e -> {
            String sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                items.remove(sel);
                configManager.getConfig().getLockedApps().remove(sel);
                configManager.save();
            }
        });

        clearBtn.setOnAction(e -> {
            items.clear();
            configManager.getConfig().getLockedApps().clear();
            configManager.save();
        });

        toolbar.getChildren().addAll(addBtn, removeBtn, clearBtn);

        // PIN section
        TitledPane pinPane = buildPinSection();

        content.getChildren().addAll(hint, listView, toolbar, pinPane);
        return content;
    }

    private TitledPane buildPinSection() {
        VBox inner = new VBox(8);
        inner.setPadding(new Insets(12));

        Label desc = new Label("Set a fallback PIN in case the camera fails.");
        desc.getStyleClass().add("hint-label");

        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        PasswordField pinField = new PasswordField();
        pinField.setPromptText("Enter 4–8 digit PIN");
        pinField.setPrefWidth(160);

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm PIN");
        confirmField.setPrefWidth(160);

        Button savePin = new Button("Save PIN");
        savePin.getStyleClass().add("btn-primary");

        Label statusLbl = new Label();
        statusLbl.getStyleClass().add("status-label");

        savePin.setOnAction(e -> {
            String pin     = pinField.getText().trim();
            String confirm = confirmField.getText().trim();
            if (pin.isEmpty()) {
                statusLbl.setText("PIN cannot be empty");
                statusLbl.getStyleClass().setAll("status-label", "status-error");
                return;
            }
            if (!pin.matches("\\d{4,8}")) {
                statusLbl.setText("PIN must be 4–8 digits");
                statusLbl.getStyleClass().setAll("status-label", "status-error");
                return;
            }
            if (!pin.equals(confirm)) {
                statusLbl.setText("PINs do not match");
                statusLbl.getStyleClass().setAll("status-label", "status-error");
                return;
            }
            configManager.setPin(pin);
            pinField.clear();
            confirmField.clear();
            statusLbl.setText("✓ PIN saved");
            statusLbl.getStyleClass().setAll("status-label", "status-ok");
        });

        row.getChildren().addAll(pinField, confirmField, savePin, statusLbl);
        inner.getChildren().addAll(desc, row);

        TitledPane pane = new TitledPane("Fallback PIN", inner);
        pane.setExpanded(false);
        pane.getStyleClass().add("titled-pane");
        return pane;
    }

    // -----------------------------------------------------------------------
    // Tab 2 – Face Registration
    // -----------------------------------------------------------------------

    private Node buildFaceRegistrationTab() {
        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.TOP_CENTER);
        content.getStyleClass().add("tab-content");

        Label hint = new Label(
                "Register your face so the app can recognise you automatically.\n" +
                "Ensure good lighting and look directly at the camera.");
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);

        // Stats
        Label statsLabel = new Label(buildStatsText());
        statsLabel.getStyleClass().add("stats-label");

        // Buttons
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER);

        Button registerBtn = new Button("📷  Register Face");
        Button clearBtn    = new Button("🗑  Clear Face Data");

        registerBtn.getStyleClass().add("btn-primary");
        registerBtn.setPrefWidth(180);
        clearBtn.getStyleClass().add("btn-danger");
        clearBtn.setPrefWidth(180);

        registerBtn.setOnAction(e -> {
            FaceRegistrationDialog dialog = new FaceRegistrationDialog(
                    (Stage) content.getScene().getWindow(),
                    configManager);
            dialog.showAndWait();
            statsLabel.setText(buildStatsText());
        });

        clearBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Clear all face data? You will need to re-register.",
                    ButtonType.YES, ButtonType.NO);
            confirm.setTitle("Confirm");
            confirm.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.YES) {
                    // The engine will be reset on next service start
                    try {
                        java.nio.file.Path samplesDir =
                                configManager.getDataDir().resolve("samples");
                        java.nio.file.Path modelFile =
                                configManager.getDataDir().resolve("face_model.xml");
                        java.nio.file.Files.deleteIfExists(modelFile);
                        if (java.nio.file.Files.isDirectory(samplesDir)) {
                            try (var ds = java.nio.file.Files.newDirectoryStream(
                                    samplesDir, "face_*.png")) {
                                for (var p : ds) {
                                    java.nio.file.Files.deleteIfExists(p);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        // Ignore
                    }
                    statsLabel.setText(buildStatsText());
                }
            });
        });

        btnRow.getChildren().addAll(registerBtn, clearBtn);
        content.getChildren().addAll(hint, statsLabel, btnRow);
        return content;
    }

    private String buildStatsText() {
        java.nio.file.Path samplesDir = configManager.getDataDir().resolve("samples");
        long count = 0;
        if (java.nio.file.Files.isDirectory(samplesDir)) {
            try (var ds = java.nio.file.Files.newDirectoryStream(samplesDir, "face_*.png")) {
                for (var ignored : ds) count++;
            } catch (Exception ignored) {}
        }
        java.nio.file.Path model = configManager.getDataDir().resolve("face_model.xml");
        boolean trained = java.nio.file.Files.exists(model);
        return String.format("Training samples: %d   |   Model trained: %s",
                             count, trained ? "Yes ✓" : "No ✗");
    }

    // -----------------------------------------------------------------------
    // Tab 3 – Access Log
    // -----------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Node buildAccessLogTab() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("tab-content");

        Label hint = new Label("Recent access attempts (newest last):");
        hint.getStyleClass().add("hint-label");

        TableView<AppConfig.AccessLogEntry> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("log-table");

        TableColumn<AppConfig.AccessLogEntry, String> tsCol =
                new TableColumn<>("Timestamp");
        tsCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));
        tsCol.setPrefWidth(180);

        TableColumn<AppConfig.AccessLogEntry, String> appCol =
                new TableColumn<>("Application");
        appCol.setCellValueFactory(new PropertyValueFactory<>("exeName"));
        appCol.setPrefWidth(200);

        TableColumn<AppConfig.AccessLogEntry, String> resultCol =
                new TableColumn<>("Result");
        resultCol.setCellValueFactory(new PropertyValueFactory<>("outcome"));
        resultCol.setPrefWidth(100);

        table.getColumns().addAll(tsCol, appCol, resultCol);

        List<AppConfig.AccessLogEntry> entries =
                configManager.getConfig().getAccessLog();
        ObservableList<AppConfig.AccessLogEntry> data =
                FXCollections.observableArrayList(entries);
        table.setItems(data);

        Button refreshBtn = new Button("↻  Refresh");
        refreshBtn.getStyleClass().add("btn-secondary");
        refreshBtn.setOnAction(e -> {
            data.setAll(configManager.getConfig().getAccessLog());
        });

        Button clearBtn = new Button("Clear Log");
        clearBtn.getStyleClass().add("btn-danger");
        clearBtn.setOnAction(e -> {
            configManager.getConfig().getAccessLog().clear();
            configManager.save();
            data.clear();
        });

        HBox toolbar = new HBox(8, refreshBtn, clearBtn);
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        VBox.setVgrow(table, Priority.ALWAYS);
        content.getChildren().addAll(hint, table, toolbar);
        return content;
    }
}
