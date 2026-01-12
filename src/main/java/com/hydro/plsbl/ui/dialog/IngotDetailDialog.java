package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.IngotDTO;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Detail-Dialog für Barren
 */
public class IngotDetailDialog extends Dialog {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final IngotDTO ingot;
    private Consumer<IngotDTO> onEdit;
    private Consumer<IngotDTO> onDelete;

    public IngotDetailDialog(IngotDTO ingot) {
        this.ingot = ingot;
        setHeaderTitle("Barren " + ingot.getIngotNo());
        setWidth("550px");
        setModal(true);
        setDraggable(true);

        createContent();
        createFooter();
    }

    private void createContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        // === Allgemeine Informationen ===
        H4 generalTitle = new H4("Allgemein");
        generalTitle.getStyle().set("margin", "0");

        FormLayout generalForm = new FormLayout();
        generalForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        generalForm.addFormItem(createValueSpan(String.valueOf(ingot.getId())), "ID");
        generalForm.addFormItem(createValueSpan(ingot.getIngotNo()), "Barren-Nr");
        generalForm.addFormItem(
            createValueSpan(ingot.getProductNo() != null ? ingot.getProductNo() : "-"),
            "Produkt"
        );
        generalForm.addFormItem(
            createValueSpan(ingot.getProductSuffix() != null ? ingot.getProductSuffix() : "-"),
            "Suffix"
        );

        content.add(generalTitle, generalForm, new Hr());

        // === Lagerort ===
        H4 locationTitle = new H4("Lagerort");
        locationTitle.getStyle().set("margin", "0");

        FormLayout locationForm = new FormLayout();
        locationForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        locationForm.addFormItem(
            createValueSpan(ingot.getStockyardNo() != null ? ingot.getStockyardNo() : "(nicht auf Lager)"),
            "Lagerplatz"
        );
        locationForm.addFormItem(
            createValueSpan(ingot.getPilePosition() != null ? String.valueOf(ingot.getPilePosition()) : "-"),
            "Stapel-Position"
        );
        locationForm.addFormItem(
            createValueSpan(ingot.getInStockSince() != null ? ingot.getInStockSince().format(DATE_FORMAT) : "-"),
            "Eingelagert seit"
        );
        locationForm.addFormItem(
            createValueSpan(ingot.getReleasedSince() != null ? ingot.getReleasedSince().format(DATE_FORMAT) : "-"),
            "Freigegeben seit"
        );

        content.add(locationTitle, locationForm, new Hr());

        // === Abmessungen ===
        H4 dimensionsTitle = new H4("Abmessungen");
        dimensionsTitle.getStyle().set("margin", "0");

        FormLayout dimensionsForm = new FormLayout();
        dimensionsForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        dimensionsForm.addFormItem(
            createValueSpan(ingot.getWeight() != null ? ingot.getWeight() + " kg" : "-"),
            "Gewicht"
        );
        dimensionsForm.addFormItem(
            createValueSpan(ingot.getLength() != null ? ingot.getLength() + " mm" : "-"),
            "Länge"
        );
        dimensionsForm.addFormItem(
            createValueSpan(ingot.getWidth() != null ? ingot.getWidth() + " mm" : "-"),
            "Breite"
        );
        dimensionsForm.addFormItem(
            createValueSpan(ingot.getThickness() != null ? ingot.getThickness() + " mm" : "-"),
            "Dicke"
        );

        content.add(dimensionsTitle, dimensionsForm, new Hr());

        // === Status-Flags ===
        H4 statusTitle = new H4("Status");
        statusTitle.getStyle().set("margin", "0");

        HorizontalLayout statusFlags = new HorizontalLayout();
        statusFlags.setSpacing(true);
        statusFlags.getStyle().set("flex-wrap", "wrap");

        statusFlags.add(createStatusBadge("Kopf gesägt", ingot.getHeadSawn()));
        statusFlags.add(createStatusBadge("Fuß gesägt", ingot.getFootSawn()));
        statusFlags.add(createStatusBadge("Schrott", ingot.getScrap()));
        statusFlags.add(createStatusBadge("Korrigiert", ingot.getRevised()));
        statusFlags.add(createStatusBadge("Gedreht", ingot.getRotated()));
        statusFlags.add(createStatusBadge("Freigegeben", ingot.isReleased()));

        content.add(statusTitle, statusFlags);
        add(content);
    }

    private void createFooter() {
        Button deleteButton = new Button("Löschen", VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> confirmDelete());

        Button editButton = new Button("Bearbeiten", VaadinIcon.EDIT.create());
        editButton.addClickListener(e -> {
            if (onEdit != null) {
                onEdit.accept(ingot);
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

        Span message = new Span("Möchten Sie den Barren " + ingot.getIngotNo() + " wirklich löschen?");
        confirmDialog.add(message);

        Button confirmButton = new Button("Löschen", VaadinIcon.TRASH.create());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        confirmButton.addClickListener(e -> {
            if (onDelete != null) {
                onDelete.accept(ingot);
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

    public void setOnEdit(Consumer<IngotDTO> onEdit) {
        this.onEdit = onEdit;
    }

    public void setOnDelete(Consumer<IngotDTO> onDelete) {
        this.onDelete = onDelete;
    }

    private Span createValueSpan(String value) {
        Span span = new Span(value);
        span.getStyle().set("font-weight", "500");
        return span;
    }

    private Span createStatusBadge(String label, Boolean value) {
        Span badge = new Span(label);
        badge.getElement().getThemeList().add("badge");
        badge.getElement().getThemeList().add("small");

        if (Boolean.TRUE.equals(value)) {
            badge.getElement().getThemeList().add("success");
        } else {
            badge.getElement().getThemeList().add("contrast");
            badge.getStyle().set("opacity", "0.5");
        }

        return badge;
    }
}
