package com.hydro.plsbl.ui.dialog;

import com.hydro.plsbl.dto.DeliveryNoteDTO;
import com.hydro.plsbl.dto.IngotDTO;
import com.hydro.plsbl.service.LieferscheinPdfService;
import com.hydro.plsbl.service.ShipmentService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.StreamRegistration;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;

import java.io.ByteArrayInputStream;
import java.time.format.DateTimeFormatter;

/**
 * Dialog zur Anzeige eines Lieferscheins nach erfolgreicher Lieferung
 */
public class LieferungBestaetigenDialog extends Dialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final DeliveryNoteDTO deliveryNote;
    private final LieferscheinPdfService pdfService;
    private final ShipmentService shipmentService;

    public LieferungBestaetigenDialog(DeliveryNoteDTO deliveryNote, LieferscheinPdfService pdfService,
                                       ShipmentService shipmentService) {
        this.deliveryNote = deliveryNote;
        this.pdfService = pdfService;
        this.shipmentService = shipmentService;
        setHeaderTitle("Lieferschein " + deliveryNote.getDeliveryNoteNumber());
        setWidth("700px");
        setHeight("600px");
        setModal(true);
        setDraggable(true);
        setResizable(true);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(true);
        content.setSpacing(true);
        content.setSizeFull();

        content.add(createHeader(deliveryNote));
        content.add(createInfoSection(deliveryNote));
        content.add(new Hr());

        H4 ingotTitle = new H4("Gelieferte Barren");
        ingotTitle.getStyle().set("margin", "0");
        content.add(ingotTitle);

        Grid<IngotDTO> ingotGrid = createIngotGrid(deliveryNote);
        content.add(ingotGrid);
        content.setFlexGrow(1, ingotGrid);

        content.add(createSummary(deliveryNote));
        add(content);
        createFooter();
    }

    private HorizontalLayout createHeader(DeliveryNoteDTO note) {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(FlexComponent.Alignment.CENTER);

        H2 title = new H2("Lieferschein");
        title.getStyle().set("margin", "0").set("color", "#1976D2");

        Div noteInfo = new Div();
        noteInfo.getStyle().set("text-align", "right");

        Span noteNumber = new Span("Nr: " + note.getDeliveryNoteNumber());
        noteNumber.getStyle().set("font-weight", "bold").set("font-size", "18px").set("display", "block");

        Span noteDate = new Span("Datum: " + note.getCreatedAt().format(DATE_TIME_FORMAT));
        noteDate.getStyle().set("color", "#666").set("display", "block");

        noteInfo.add(noteNumber, noteDate);
        header.add(title, noteInfo);
        header.expand(title);

        return header;
    }

    private Div createInfoSection(DeliveryNoteDTO note) {
        Div section = new Div();
        section.getStyle()
            .set("display", "grid")
            .set("grid-template-columns", "1fr 1fr")
            .set("gap", "20px")
            .set("padding", "10px")
            .set("background-color", "#f5f5f5")
            .set("border-radius", "4px");

        Div customerCol = new Div();
        customerCol.add(createLabel("Kunde"));
        String customerText = (note.getCustomerNumber() != null ? note.getCustomerNumber() : "") +
            (note.getCustomerName() != null ? " - " + note.getCustomerName() : "");
        customerCol.add(createValue(customerText.isEmpty() ? "-" : customerText));
        if (note.getCustomerAddress() != null && !note.getCustomerAddress().isEmpty()) {
            Span address = new Span(note.getCustomerAddress().replace("\n", ", "));
            address.getStyle().set("display", "block").set("font-size", "12px").set("color", "#666");
            customerCol.add(address);
        }

        Div orderCol = new Div();
        orderCol.add(createLabel("Abruf / Auftrag"));
        orderCol.add(createValue(note.getCalloffNumber() != null ? note.getCalloffNumber() : "-"));
        if (note.getOrderNumber() != null) {
            Span orderInfo = new Span("Auftrag: " + note.getOrderDisplay());
            orderInfo.getStyle().set("display", "block").set("font-size", "12px").set("color", "#666");
            orderCol.add(orderInfo);
        }
        Span destInfo = new Span("Lieferort: " + (note.getDestination() != null ? note.getDestination() : "-"));
        destInfo.getStyle().set("display", "block").set("font-size", "12px").set("color", "#666");
        orderCol.add(destInfo);

        section.add(customerCol, orderCol);

        if (note.getSapProductNumber() != null) {
            Div productRow = new Div();
            productRow.getStyle().set("grid-column", "span 2").set("margin-top", "10px");
            productRow.add(createLabel("Produkt"));
            productRow.add(createValue(note.getSapProductNumber() +
                (note.getProductName() != null ? " - " + note.getProductName() : "")));
            section.add(productRow);
        }

        return section;
    }

    private Span createLabel(String text) {
        Span label = new Span(text);
        label.getStyle()
            .set("font-weight", "bold")
            .set("font-size", "11px")
            .set("color", "#888")
            .set("text-transform", "uppercase")
            .set("display", "block");
        return label;
    }

    private Span createValue(String text) {
        Span value = new Span(text != null ? text : "-");
        value.getStyle().set("font-size", "14px").set("display", "block");
        return value;
    }

    private Grid<IngotDTO> createIngotGrid(DeliveryNoteDTO note) {
        Grid<IngotDTO> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setAllRowsVisible(true);
        grid.setMaxHeight("200px");

        grid.addColumn(IngotDTO::getIngotNo).setHeader("Barren-Nr.").setWidth("150px").setFlexGrow(0);
        grid.addColumn(IngotDTO::getStockyardNo).setHeader("Lagerplatz").setWidth("100px").setFlexGrow(0);
        grid.addColumn(dto -> dto.getLength() != null ? dto.getLength() + " mm" : "-").setHeader("Laenge").setWidth("100px").setFlexGrow(0);
        grid.addColumn(dto -> dto.getWeight() != null ? dto.getWeight() + " kg" : "-").setHeader("Gewicht").setWidth("100px").setFlexGrow(0);

        grid.setItems(note.getDeliveredIngots());
        return grid;
    }

    private HorizontalLayout createSummary(DeliveryNoteDTO note) {
        HorizontalLayout summary = new HorizontalLayout();
        summary.setWidthFull();
        summary.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        summary.getStyle()
            .set("padding", "10px")
            .set("background-color", "#e3f2fd")
            .set("border-radius", "4px")
            .set("margin-top", "10px");

        Span countLabel = new Span("Anzahl Barren: ");
        countLabel.getStyle().set("font-weight", "bold");
        Span countValue = new Span(String.valueOf(note.getTotalCount()));
        countValue.getStyle().set("margin-right", "30px");

        Span weightLabel = new Span("Gesamtgewicht: ");
        weightLabel.getStyle().set("font-weight", "bold");
        Span weightValue = new Span(note.getTotalWeight() + " kg");

        summary.add(countLabel, countValue, weightLabel, weightValue);
        return summary;
    }

    private void createFooter() {
        Button printButton = new Button("PDF Drucken", VaadinIcon.PRINT.create());
        printButton.addClickListener(e -> downloadPdf());

        Button closeButton = new Button("Schliessen", VaadinIcon.CLOSE.create());
        closeButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeButton.addClickListener(e -> close());

        getFooter().add(printButton, closeButton);
    }

    private void downloadPdf() {
        try {
            // PDF generieren
            byte[] pdfContent = pdfService.generatePdf(deliveryNote);

            // Als gedruckt markieren
            if (deliveryNote.getId() != null) {
                shipmentService.markAsPrinted(deliveryNote.getId());
            }

            // PDF als Download anbieten
            String fileName = "Lieferschein_" + deliveryNote.getDeliveryNoteNumber() + ".pdf";
            StreamResource resource = new StreamResource(fileName,
                () -> new ByteArrayInputStream(pdfContent));
            resource.setContentType("application/pdf");
            resource.setCacheTime(0);

            // Content-Disposition Header für Download setzen
            resource.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

            // Resource bei VaadinSession registrieren
            StreamRegistration registration = VaadinSession.getCurrent()
                .getResourceRegistry()
                .registerResource(resource);

            // URL ermitteln und in neuem Tab öffnen
            String downloadUrl = registration.getResourceUri().toString();
            UI.getCurrent().getPage().open(downloadUrl, "_blank");

            Notification.show("Lieferschein " + deliveryNote.getDeliveryNoteNumber() + " wird heruntergeladen",
                3000, Notification.Position.BOTTOM_CENTER)
                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

        } catch (Exception e) {
            Notification.show("Fehler beim Erstellen des PDF: " + e.getMessage(),
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            e.printStackTrace();
        }
    }
}
