package com.faceunlocker.ui;

import com.faceunlocker.config.ConfigManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Small modal dialog that prompts the user to enter their fallback PIN.
 * Returns {@link Boolean#TRUE} if the correct PIN was entered,
 * {@link Boolean#FALSE} otherwise.
 */
public class PinFallbackDialog extends Dialog<Boolean> {

    private final ConfigManager configManager;
    private PasswordField pinField;

    public PinFallbackDialog(Stage owner, ConfigManager configManager) {
        this.configManager = configManager;
        initOwner(owner);
        initModality(Modality.WINDOW_MODAL);
        setTitle("Enter Fallback PIN");
        buildContent();
        setResultConverter(this::mapResult);
    }

    // -----------------------------------------------------------------------
    // Build
    // -----------------------------------------------------------------------

    private void buildContent() {
        VBox content = new VBox(12);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);
        content.setPrefWidth(300);

        Label lbl = new Label("Camera unavailable. Enter your fallback PIN:");
        lbl.setWrapText(true);
        lbl.getStyleClass().add("hint-label");

        pinField = new PasswordField();
        pinField.setPromptText("PIN");
        pinField.setMaxWidth(160);
        pinField.getStyleClass().add("pin-field");

        Label errorLbl = new Label();
        errorLbl.getStyleClass().add("status-error");

        content.getChildren().addAll(lbl, pinField, errorLbl);
        getDialogPane().setContent(content);

        ButtonType submitType = new ButtonType("Unlock", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = ButtonType.CANCEL;
        getDialogPane().getButtonTypes().addAll(submitType, cancelType);

        Button submitBtn = (Button) getDialogPane().lookupButton(submitType);
        submitBtn.getStyleClass().add("btn-primary");

        // Validate before closing
        submitBtn.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (!configManager.verifyPin(pinField.getText())) {
                e.consume();
                errorLbl.setText("Incorrect PIN – try again");
                pinField.clear();
            }
        });
    }

    // -----------------------------------------------------------------------
    // Result converter
    // -----------------------------------------------------------------------

    private Boolean mapResult(ButtonType buttonType) {
        if (buttonType != null && buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
            return configManager.verifyPin(pinField.getText());
        }
        return Boolean.FALSE;
    }
}
