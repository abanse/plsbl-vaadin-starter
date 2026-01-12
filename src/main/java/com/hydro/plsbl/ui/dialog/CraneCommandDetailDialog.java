package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.CraneCommandDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.function.Consumer;

/**
 * Detail-Dialog für Kran-Kommandos
 */
public class CraneCommandDetailDialog extends Dialog {

    private final CraneCommandDTO command;
    private Consumer<CraneCommandDTO> onEdit;
    private Consumer<CraneCommandDTO> onDelete;

    public CraneCommandDetailDialog(CraneCommandDTO command) {
        this.command = command;
        setHeaderTitle("Kran-Kommando #" + command.getId());
        setWidth("500px");
        setModal(true);
        setDraggable(true);

        // Content
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        // Formular mit Details
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        // ID
        form.addFormItem(createValueSpan(String.valueOf(command.getId())), "ID");

        // TableSerial
        form.addFormItem(
            createValueSpan(command.getTableSerial() != null ? String.valueOf(command.getTableSerial()) : "-"),
            "Table Serial"
        );

        // Kommando-Typ
        Span cmdTypeSpan = createBadge(command.getCmdTypeDisplay(), "primary");
        form.addFormItem(cmdTypeSpan, "Kommando-Typ");

        // Kran-Modus
        Span craneModeSpan = createBadge(command.getCraneModeDisplay(), "contrast");
        form.addFormItem(craneModeSpan, "Kran-Modus");

        // Route
        form.addFormItem(createValueSpan(command.getRoute()), "Route");

        // Von Lagerplatz
        form.addFormItem(
            createValueSpan(command.getFromYardNo() != null ? command.getFromYardNo() : "-"),
            "Von Lagerplatz"
        );

        // Nach Lagerplatz
        form.addFormItem(
            createValueSpan(command.getToYardNo() != null ? command.getToYardNo() : "-"),
            "Nach Lagerplatz"
        );

        // Barren
        form.addFormItem(
            createValueSpan(command.getIngotNo() != null ? command.getIngotNo() : "-"),
            "Barren-Nr"
        );

        // Rotation
        form.addFormItem(
            createValueSpan(command.getRotate() != null ? command.getRotate() + "°" : "-"),
            "Rotation"
        );

        content.add(form);
        add(content);

        // Footer mit Löschen-, Bearbeiten- und Schließen-Button
        Button deleteButton = new Button("Löschen", VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> confirmDelete());

        Button editButton = new Button("Bearbeiten", VaadinIcon.EDIT.create());
        editButton.addClickListener(e -> {
            if (onEdit != null) {
                onEdit.accept(command);
            }
            close();
        });

        Button closeButton = new Button("Schließen", VaadinIcon.CLOSE.create());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeButton.addClickListener(e -> close());

        HorizontalLayout footer = new HorizontalLayout(deleteButton, editButton, closeButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private void confirmDelete() {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Löschen bestätigen");
        confirmDialog.setModal(true);

        Span message = new Span("Möchten Sie das Kran-Kommando #" + command.getId() + " wirklich löschen?");
        confirmDialog.add(message);

        Button confirmButton = new Button("Löschen", VaadinIcon.TRASH.create());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        confirmButton.addClickListener(e -> {
            if (onDelete != null) {
                onDelete.accept(command);
            }
            confirmDialog.close();
            close();
        });

        Button cancelButton = new Button("Abbrechen");
        cancelButton.addClickListener(e -> confirmDialog.close());

        HorizontalLayout buttons = new HorizontalLayout(cancelButton, confirmButton);
        buttons.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        confirmDialog.getFooter().add(buttons);

        confirmDialog.open();
    }

    public void setOnEdit(Consumer<CraneCommandDTO> onEdit) {
        this.onEdit = onEdit;
    }

    public void setOnDelete(Consumer<CraneCommandDTO> onDelete) {
        this.onDelete = onDelete;
    }

    private Span createValueSpan(String value) {
        Span span = new Span(value);
        span.getStyle().set("font-weight", "500");
        return span;
    }

    private Span createBadge(String text, String theme) {
        Span badge = new Span(text);
        badge.getElement().getThemeList().add("badge");
        badge.getElement().getThemeList().add(theme);
        return badge;
    }
}
