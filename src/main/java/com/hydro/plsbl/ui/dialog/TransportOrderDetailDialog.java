package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.TransportOrderDTO;
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

import java.util.function.Consumer;

/**
 * Detail-Dialog für Transportaufträge
 */
public class TransportOrderDetailDialog extends Dialog {

    private final TransportOrderDTO order;
    private Consumer<TransportOrderDTO> onEdit;
    private Consumer<TransportOrderDTO> onDelete;

    public TransportOrderDetailDialog(TransportOrderDTO order) {
        this.order = order;
        setHeaderTitle("Transportauftrag #" + order.getId());
        setWidth("550px");
        setModal(true);
        setDraggable(true);

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

        generalForm.addFormItem(createValueSpan(String.valueOf(order.getId())), "ID");
        generalForm.addFormItem(
            createValueSpan(order.getTableSerial() != null ? String.valueOf(order.getTableSerial()) : "-"),
            "Table Serial"
        );
        generalForm.addFormItem(
            createValueSpan(order.getTransportNo() != null ? order.getTransportNo() : "-"),
            "Transport-Nr"
        );
        generalForm.addFormItem(
            createValueSpan(order.getNormText() != null ? order.getNormText() : "-"),
            "Norm"
        );

        content.add(generalTitle, generalForm, new Hr());

        // === Route ===
        H4 routeTitle = new H4("Route");
        routeTitle.getStyle().set("margin", "0");

        FormLayout routeForm = new FormLayout();
        routeForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        // Von
        String fromInfo = buildLocationInfo(order.getFromYardNo(), order.getFromPilePosition());
        routeForm.addFormItem(createLocationBadge(fromInfo, "contrast"), "Von");

        // Nach
        String toInfo = buildLocationInfo(order.getToYardNo(), order.getToPilePosition());
        routeForm.addFormItem(createLocationBadge(toInfo, "primary"), "Nach");

        // Route-Übersicht
        Span routeSpan = new Span(order.getRouteWithPositions());
        routeSpan.getStyle()
            .set("font-size", "var(--lumo-font-size-l)")
            .set("font-weight", "600");
        routeForm.addFormItem(routeSpan, "Route");

        content.add(routeTitle, routeForm, new Hr());

        // === Material ===
        H4 materialTitle = new H4("Material");
        materialTitle.getStyle().set("margin", "0");

        FormLayout materialForm = new FormLayout();
        materialForm.setResponsiveSteps(
            new FormLayout.ResponsiveStep("0", 1),
            new FormLayout.ResponsiveStep("400px", 2)
        );

        materialForm.addFormItem(
            createValueSpan(order.getIngotNo() != null ? order.getIngotNo() : "-"),
            "Barren-Nr"
        );
        materialForm.addFormItem(
            createValueSpan(order.getCalloffId() != null ? String.valueOf(order.getCalloffId()) : "-"),
            "Calloff-ID"
        );

        content.add(materialTitle, materialForm);
        add(content);

        // Footer mit Löschen-, Bearbeiten- und Schließen-Button
        Button deleteButton = new Button("Löschen", VaadinIcon.TRASH.create());
        deleteButton.addThemeVariants(ButtonVariant.LUMO_ERROR);
        deleteButton.addClickListener(e -> confirmDelete());

        Button editButton = new Button("Bearbeiten", VaadinIcon.EDIT.create());
        editButton.addClickListener(e -> {
            if (onEdit != null) {
                onEdit.accept(order);
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

        Span message = new Span("Möchten Sie den Transportauftrag #" + order.getId() + " wirklich löschen?");
        confirmDialog.add(message);

        Button confirmButton = new Button("Löschen", VaadinIcon.TRASH.create());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_PRIMARY);
        confirmButton.addClickListener(e -> {
            if (onDelete != null) {
                onDelete.accept(order);
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

    public void setOnEdit(Consumer<TransportOrderDTO> onEdit) {
        this.onEdit = onEdit;
    }

    public void setOnDelete(Consumer<TransportOrderDTO> onDelete) {
        this.onDelete = onDelete;
    }

    private String buildLocationInfo(String yardNo, Integer pilePosition) {
        StringBuilder sb = new StringBuilder();
        sb.append(yardNo != null ? yardNo : "-");
        if (pilePosition != null) {
            sb.append(" [Pos: ").append(pilePosition).append("]");
        }
        return sb.toString();
    }

    private Span createValueSpan(String value) {
        Span span = new Span(value);
        span.getStyle().set("font-weight", "500");
        return span;
    }

    private Span createLocationBadge(String text, String theme) {
        Span badge = new Span(text);
        badge.getElement().getThemeList().add("badge");
        badge.getElement().getThemeList().add(theme);
        badge.getStyle().set("font-size", "var(--lumo-font-size-s)");
        return badge;
    }
}
