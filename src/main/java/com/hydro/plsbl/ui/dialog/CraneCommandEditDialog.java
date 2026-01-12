package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.CraneCommandDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;

import java.util.function.Consumer;

/**
 * Edit-Dialog für Kran-Kommandos
 */
public class CraneCommandEditDialog extends Dialog {

    private final CraneCommandDTO command;
    private final boolean isNew;
    private Consumer<CraneCommandDTO> onSave;

    private ComboBox<String> cmdTypeField;
    private ComboBox<String> craneModeField;
    private IntegerField rotateField;
    private TextField ingotNoField;

    public CraneCommandEditDialog(CraneCommandDTO command, boolean isNew) {
        this.command = command != null ? command : new CraneCommandDTO();
        this.isNew = isNew;

        setHeaderTitle(isNew ? "Neues Kran-Kommando" : "Kran-Kommando #" + this.command.getId() + " bearbeiten");
        setWidth("500px");
        setModal(true);
        setDraggable(true);
        setCloseOnOutsideClick(false);

        createContent();
        createFooter();

        if (!isNew) {
            populateFields();
        }
    }

    private void createContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        // Kommando-Typ
        cmdTypeField = new ComboBox<>("Kommando-Typ");
        cmdTypeField.setItems("M", "P", "D", "R", "K");
        cmdTypeField.setItemLabelGenerator(this::getCmdTypeLabel);
        cmdTypeField.setRequired(true);
        cmdTypeField.setWidthFull();

        // Kran-Modus
        craneModeField = new ComboBox<>("Kran-Modus");
        craneModeField.setItems("A", "M", "S");
        craneModeField.setItemLabelGenerator(this::getCraneModeLabel);
        craneModeField.setRequired(true);
        craneModeField.setWidthFull();

        // Rotation
        rotateField = new IntegerField("Rotation (°)");
        rotateField.setMin(0);
        rotateField.setMax(360);
        rotateField.setStep(90);
        rotateField.setValue(0);
        rotateField.setWidthFull();

        // Barren-Nr (nur Anzeige, da Foreign Key)
        ingotNoField = new TextField("Barren-Nr");
        ingotNoField.setReadOnly(true);
        ingotNoField.setWidthFull();
        ingotNoField.setHelperText("Wird über Barren-Auswahl gesetzt");

        form.add(cmdTypeField, craneModeField, rotateField, ingotNoField);

        content.add(form);
        add(content);
    }

    private void createFooter() {
        Button saveButton = new Button("Speichern", VaadinIcon.CHECK.create());
        saveButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveButton.addClickListener(e -> save());

        Button cancelButton = new Button("Abbrechen", VaadinIcon.CLOSE.create());
        cancelButton.addClickListener(e -> close());

        HorizontalLayout footer = new HorizontalLayout(cancelButton, saveButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private void populateFields() {
        if (command.getCmdType() != null) {
            cmdTypeField.setValue(command.getCmdType());
        }
        if (command.getCraneMode() != null) {
            craneModeField.setValue(command.getCraneMode());
        }
        if (command.getRotate() != null) {
            rotateField.setValue(command.getRotate());
        }
        if (command.getIngotNo() != null) {
            ingotNoField.setValue(command.getIngotNo());
        }
    }

    private void save() {
        // Validierung
        if (cmdTypeField.isEmpty()) {
            Notification.show("Bitte Kommando-Typ auswählen", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }
        if (craneModeField.isEmpty()) {
            Notification.show("Bitte Kran-Modus auswählen", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        // Werte übernehmen
        command.setCmdType(cmdTypeField.getValue());
        command.setCraneMode(craneModeField.getValue());
        command.setRotate(rotateField.getValue());

        // Callback aufrufen
        if (onSave != null) {
            onSave.accept(command);
        }

        Notification.show("Gespeichert", 2000, Notification.Position.BOTTOM_START)
            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        close();
    }

    public void setOnSave(Consumer<CraneCommandDTO> onSave) {
        this.onSave = onSave;
    }

    private String getCmdTypeLabel(String code) {
        return switch (code) {
            case "M" -> "Bewegen (M)";
            case "P" -> "Aufnehmen (P)";
            case "D" -> "Ablegen (D)";
            case "R" -> "Drehen (R)";
            case "K" -> "Parken (K)";
            default -> code;
        };
    }

    private String getCraneModeLabel(String code) {
        return switch (code) {
            case "A" -> "Automatik (A)";
            case "M" -> "Manuell (M)";
            case "S" -> "Halbautomatik (S)";
            default -> code;
        };
    }
}
