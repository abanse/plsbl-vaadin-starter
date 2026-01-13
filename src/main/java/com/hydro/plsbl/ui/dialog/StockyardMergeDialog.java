package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.StockyardDTO;
import com.hydro.plsbl.service.StockyardService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog zum Zusammenfügen von zwei benachbarten kurzen Lagerplätzen
 * zu einem langen Lagerplatz.
 */
public class StockyardMergeDialog extends Dialog {

    private final StockyardDTO sourceStockyard;
    private final StockyardService stockyardService;

    private ComboBox<StockyardDTO> targetComboBox;
    private Button mergeButton;
    private Consumer<StockyardDTO> onMerged;

    public StockyardMergeDialog(StockyardDTO sourceStockyard,
                                 StockyardService stockyardService) {
        this.sourceStockyard = sourceStockyard;
        this.stockyardService = stockyardService;

        setHeaderTitle("Lagerplätze zusammenfügen");
        setWidth("500px");
        setModal(true);
        setDraggable(true);
        setCloseOnOutsideClick(false);

        createContent();
        createFooter();
    }

    private void createContent() {
        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);

        // Quell-Info
        content.add(new H4("Quell-Lagerplatz: " + sourceStockyard.getYardNumber()));

        Span coordInfo = new Span("Koordinaten: (" + sourceStockyard.getXCoordinate()
            + ", " + sourceStockyard.getYCoordinate() + ")");
        coordInfo.getStyle().set("color", "gray");
        content.add(coordInfo);

        String usageName = sourceStockyard.getUsage() != null
            ? sourceStockyard.getUsage().getDisplayName()
            : "Unbekannt";
        Span usageInfo = new Span("Verwendung: " + usageName
            + " (" + sourceStockyard.getLength() + " mm)");
        content.add(usageInfo);

        // Ziel-Auswahl
        content.add(new H4("Benachbarten Lagerplatz auswählen"));
        content.add(createTargetComboBox());

        // Info-Text
        Span mergeInfo = new Span("Nach dem Zusammenfügen entsteht ein langer Lagerplatz " +
            "(2500 mm, max. 6 Barren).");
        mergeInfo.getStyle().set("font-size", "12px").set("color", "#666");
        content.add(mergeInfo);

        add(content);
    }

    private ComboBox<StockyardDTO> createTargetComboBox() {
        targetComboBox = new ComboBox<>("Nachbar-Lagerplatz");
        targetComboBox.setWidthFull();
        targetComboBox.setPlaceholder("Lagerplatz auswählen...");
        targetComboBox.setItemLabelGenerator(this::getStockyardLabel);

        // Benachbarte leere Plätze laden
        try {
            List<StockyardDTO> adjacent = stockyardService
                .findAdjacentEmptyStockyards(sourceStockyard.getId());
            targetComboBox.setItems(adjacent);

            if (adjacent.isEmpty()) {
                targetComboBox.setHelperText("Keine benachbarten leeren kurzen Plätze gefunden");
            }
        } catch (Exception e) {
            Notification.show("Fehler: " + e.getMessage(), 5000,
                Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }

        targetComboBox.addValueChangeListener(e -> updateMergeButton());

        return targetComboBox;
    }

    private String getStockyardLabel(StockyardDTO yard) {
        String direction = yard.getXCoordinate() < sourceStockyard.getXCoordinate()
            ? "links" : "rechts";
        return yard.getYardNumber() + " (" + direction + ")";
    }

    private void updateMergeButton() {
        if (mergeButton != null) {
            mergeButton.setEnabled(targetComboBox.getValue() != null);
        }
    }

    private void createFooter() {
        mergeButton = new Button("Zusammenfügen", VaadinIcon.CONNECT.create());
        mergeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        mergeButton.setEnabled(false);
        mergeButton.addClickListener(e -> executeMerge());

        Button cancelButton = new Button("Abbrechen", VaadinIcon.CLOSE.create());
        cancelButton.addClickListener(e -> close());

        HorizontalLayout footer = new HorizontalLayout(cancelButton, mergeButton);
        footer.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        footer.setWidthFull();
        getFooter().add(footer);
    }

    private void executeMerge() {
        StockyardDTO target = targetComboBox.getValue();
        if (target == null) {
            return;
        }

        try {
            StockyardDTO merged = stockyardService.mergeStockyards(
                sourceStockyard.getId(), target.getId());

            Notification.show(
                "Lagerplätze zusammengefügt: " + merged.getYardNumber() + " (Lang)",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            if (onMerged != null) {
                onMerged.accept(merged);
            }

            close();

        } catch (Exception e) {
            Notification.show("Fehler: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    public void setOnMerged(Consumer<StockyardDTO> onMerged) {
        this.onMerged = onMerged;
    }
}
