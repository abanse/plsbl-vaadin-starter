package com.hydro.plsbl.service;

import com.hydro.plsbl.entity.transdata.Shipment;
import com.hydro.plsbl.entity.transdata.ShipmentLine;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import com.hydro.plsbl.dto.DeliveryNoteDTO;
import com.hydro.plsbl.dto.IngotDTO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service zur PDF-Generierung von Lieferscheinen
 */
@Service
public class LieferscheinPdfService {

    private static final Logger log = LoggerFactory.getLogger(LieferscheinPdfService.class);
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yy HH:mm");

    private final ShipmentService shipmentService;

    // Schriftarten
    private Font fontTitle;
    private Font fontHeader;
    private Font fontNormal;
    private Font fontBold;
    private Font fontSmall;
    private Font fontTableHeader;

    public LieferscheinPdfService(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
        initFonts();
    }

    private void initFonts() {
        fontTitle = new Font(Font.HELVETICA, 14, Font.BOLD);
        fontHeader = new Font(Font.HELVETICA, 10, Font.NORMAL);
        fontNormal = new Font(Font.HELVETICA, 10, Font.NORMAL);
        fontBold = new Font(Font.HELVETICA, 10, Font.BOLD);
        fontSmall = new Font(Font.HELVETICA, 8, Font.NORMAL);
        fontTableHeader = new Font(Font.HELVETICA, 10, Font.BOLD);
    }

    /**
     * Generiert das Lieferschein-PDF als Byte-Array
     */
    public byte[] generatePdf(Long shipmentId) throws IOException, DocumentException {
        Shipment shipment = shipmentService.findById(shipmentId)
            .orElseThrow(() -> new IllegalArgumentException("Lieferschein nicht gefunden: " + shipmentId));

        List<ShipmentLine> lines = shipmentService.findLinesByShipmentId(shipmentId);
        int totalWeight = shipmentService.getTotalWeight(shipmentId);

        return generatePdf(shipment, lines, totalWeight);
    }

    /**
     * Generiert das Lieferschein-PDF
     */
    public byte[] generatePdf(Shipment shipment, List<ShipmentLine> lines, int totalWeight)
            throws IOException, DocumentException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Mehr Platz unten für Footer reservieren (150 statt 100)
        Document document = new Document(PageSize.A4, 50, 50, 50, 150);
        PdfWriter writer = PdfWriter.getInstance(document, baos);

        // Footer-Event-Handler registrieren
        writer.setPageEvent(new FooterPageEvent(fontSmall));

        document.open();

        // Kopfbereich
        addHeader(document, shipment);

        // Abstand
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));

        // Kundenadresse
        addCustomerAddress(document, shipment);

        // Abstand
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));

        // Positionen-Tabelle
        addPositionsTable(document, lines, totalWeight);

        document.close();

        log.info("Lieferschein-PDF generiert für Nr. {}", shipment.getShipmentNumber());
        return baos.toByteArray();
    }

    /**
     * Generiert ein Lieferschein-PDF aus DeliveryNoteDTO (für direkte Lieferung aus Abruf-View)
     */
    public byte[] generatePdf(DeliveryNoteDTO deliveryNote) throws IOException, DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 150);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        writer.setPageEvent(new FooterPageEvent(fontSmall));

        document.open();

        // Kopfbereich
        addHeader(document, deliveryNote);

        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));

        // Kundenadresse
        if (deliveryNote.getCustomerAddress() != null && !deliveryNote.getCustomerAddress().isEmpty()) {
            String[] addressLines = deliveryNote.getCustomerAddress().split("\n");
            for (String line : addressLines) {
                document.add(new Paragraph(line.trim(), fontNormal));
            }
        }

        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));

        // Positionen-Tabelle
        addPositionsTable(document, deliveryNote);

        document.close();

        log.info("Lieferschein-PDF generiert für Nr. {}", deliveryNote.getDeliveryNoteNumber());
        return baos.toByteArray();
    }

    private void addHeader(Document document, DeliveryNoteDTO note) throws DocumentException {
        Paragraph title = new Paragraph("Lieferschein-Nr: " + note.getDeliveryNoteNumber(), fontTitle);
        document.add(title);

        document.add(new Paragraph(" "));

        String deliveredDate = note.getCreatedAt() != null
            ? note.getCreatedAt().format(DATE_TIME_FORMAT)
            : LocalDateTime.now().format(DATE_TIME_FORMAT);

        document.add(new Paragraph("Auftrags-Nr: " + nvl(note.getOrderDisplay()), fontHeader));
        document.add(new Paragraph("Abruf-Nr: " + nvl(note.getCalloffNumber()), fontHeader));
        document.add(new Paragraph("Lieferort: " + nvl(note.getDestination()), fontHeader));
        document.add(new Paragraph("Kunden-Nr: " + nvl(note.getCustomerNumber()), fontHeader));
        document.add(new Paragraph("Datum: " + deliveredDate, fontHeader));
    }

    private void addPositionsTable(Document document, DeliveryNoteDTO note) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{8, 15, 30, 22, 15});

        addTableHeader(table, "Nr");
        addTableHeader(table, "Barren");
        addTableHeader(table, "Artikel");
        addTableHeader(table, "SAPArtikelNr");
        addTableHeaderRight(table, "Gewicht[kg]");

        int posNr = 1;
        for (IngotDTO ingot : note.getDeliveredIngots()) {
            addTableCell(table, String.valueOf(posNr));
            addTableCell(table, nvl(ingot.getIngotNo()));
            addTableCell(table, nvl(ingot.getProductNo()));
            addTableCell(table, nvl(note.getSapProductNumber()));
            addTableCellRight(table, ingot.getWeight() != null ? String.valueOf(ingot.getWeight()) : "");
            posNr++;
        }

        // Summenzeile
        PdfPCell emptyCell = new PdfPCell(new Phrase(""));
        emptyCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(emptyCell);
        table.addCell(emptyCell);
        table.addCell(emptyCell);

        PdfPCell sumLabelCell = new PdfPCell(new Phrase("Gesamt:", fontBold));
        sumLabelCell.setBorder(Rectangle.TOP);
        sumLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sumLabelCell.setPaddingTop(5);
        table.addCell(sumLabelCell);

        PdfPCell sumValueCell = new PdfPCell(new Phrase(String.valueOf(note.getTotalWeight()), fontBold));
        sumValueCell.setBorder(Rectangle.TOP);
        sumValueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sumValueCell.setPaddingTop(5);
        table.addCell(sumValueCell);

        document.add(table);
    }

    private void addHeader(Document document, Shipment shipment) throws DocumentException {
        // Titel
        Paragraph title = new Paragraph("Lieferschein-Nr: " + shipment.getShipmentNumber(), fontTitle);
        document.add(title);

        document.add(new Paragraph(" "));

        // Kopfdaten
        String deliveredDate = shipment.getDelivered() != null
            ? shipment.getDelivered().format(DATE_TIME_FORMAT)
            : LocalDateTime.now().format(DATE_TIME_FORMAT);

        document.add(new Paragraph("Auftrags-Nr: " + nvl(shipment.getOrderNumber()), fontHeader));
        document.add(new Paragraph("Lieferort: " + nvl(shipment.getDestination()), fontHeader));
        document.add(new Paragraph("Kunden-Nr: " + nvl(shipment.getCustomerNumber()), fontHeader));
        document.add(new Paragraph("Datum: " + deliveredDate, fontHeader));
    }

    private void addCustomerAddress(Document document, Shipment shipment) throws DocumentException {
        if (shipment.getAddress() != null && !shipment.getAddress().isEmpty()) {
            // Adresse kann mehrzeilig sein (durch \n getrennt)
            String[] addressLines = shipment.getAddress().split("\n");
            for (String line : addressLines) {
                document.add(new Paragraph(line.trim(), fontNormal));
            }
        }
    }

    private void addPositionsTable(Document document, List<ShipmentLine> lines, int totalWeight)
            throws DocumentException {

        // Tabelle mit 5 Spalten
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{8, 15, 30, 22, 15});

        // Header
        addTableHeader(table, "Nr");
        addTableHeader(table, "Barren");
        addTableHeader(table, "Artikel");
        addTableHeader(table, "SAPArtikelNr");
        addTableHeaderRight(table, "Gewicht[kg]");

        // Positionen mit laufender Nummer (beginnend bei 1)
        int posNr = 1;
        for (ShipmentLine line : lines) {
            // Immer laufende Nummer verwenden (position kann 0 oder null sein)
            addTableCell(table, String.valueOf(posNr));
            addTableCell(table, nvl(line.getIngotNumber()));
            addTableCell(table, nvl(line.getProductNumber()));
            addTableCell(table, nvl(line.getSapProductNumber()));
            addTableCellRight(table, line.getWeight() != null ? String.valueOf(line.getWeight()) : "");
            posNr++;
        }

        // Summenzeile
        PdfPCell emptyCell = new PdfPCell(new Phrase(""));
        emptyCell.setBorder(Rectangle.NO_BORDER);
        table.addCell(emptyCell);
        table.addCell(emptyCell);
        table.addCell(emptyCell);

        PdfPCell sumLabelCell = new PdfPCell(new Phrase("Gesamt:", fontBold));
        sumLabelCell.setBorder(Rectangle.TOP);
        sumLabelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sumLabelCell.setPaddingTop(5);
        table.addCell(sumLabelCell);

        PdfPCell sumValueCell = new PdfPCell(new Phrase(String.valueOf(totalWeight), fontBold));
        sumValueCell.setBorder(Rectangle.TOP);
        sumValueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        sumValueCell.setPaddingTop(5);
        table.addCell(sumValueCell);

        document.add(table);
    }

    private void addTableHeader(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, fontTableHeader));
        cell.setBackgroundColor(Color.WHITE);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(Color.BLACK);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addTableHeaderRight(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, fontTableHeader));
        cell.setBackgroundColor(Color.WHITE);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(Color.BLACK);
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private void addTableCell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, fontNormal));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(3);
        table.addCell(cell);
    }

    private void addTableCellRight(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, fontNormal));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(3);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private String nvl(String value) {
        return value != null ? value : "";
    }

    /**
     * PageEvent-Handler für den Footer am Seitenende
     */
    private static class FooterPageEvent extends PdfPageEventHelper {
        private final Font fontSmall;

        public FooterPageEvent(Font fontSmall) {
            this.fontSmall = fontSmall;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();

            // Hinweis-Zeile
            String hint = "---------------------- \"Messwerte aus frei programmierbarer Zusatzeinrichtung. " +
                "Die geeichten Messwerte können eingesehen werden\" ------------";

            Phrase hintPhrase = new Phrase(hint, fontSmall);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, hintPhrase,
                (document.left() + document.right()) / 2, 130, 0);

            // Footer-Tabelle
            try {
                PdfPTable footerTable = new PdfPTable(4);
                footerTable.setTotalWidth(document.right() - document.left());
                footerTable.setWidths(new float[]{25, 25, 25, 25});

                // Spalte 1: Firma
                addFooterCell(footerTable,
                    "Speira GmbH\n" +
                    "Postfach 101554\n" +
                    "41415 Neuss\n" +
                    "Koblenzer Strasse 122\n" +
                    "41468 Neuss");

                // Spalte 2: Kontakt
                addFooterCell(footerTable,
                    "Telefon: +49(0)2131 382-0\n" +
                    "Telefax: +49(0)2131 382-699\n" +
                    "info.ne@speira.com\n" +
                    "http://www.speira.com\n" +
                    "Registergericht Moenchengladbach\n" +
                    "HRB 14011");

                // Spalte 3: Bank
                addFooterCell(footerTable,
                    "Bankverbindung:\n" +
                    "Commerzbank AG Neuss\n" +
                    "BLZ 300 400 00\n" +
                    "Konto 7 586 605");

                // Spalte 4: Geschäftsführung
                addFooterCell(footerTable,
                    "Vorsitzender des Aufsichtsrates:\n" +
                    "Einar Glomnes\n" +
                    "Geschäftsführung:\n" +
                    "Dr. Pascal Wagner (Sprecher),\n" +
                    "Volker Backs");

                // Tabelle an Position zeichnen (Y = 30 vom unteren Rand)
                footerTable.writeSelectedRows(0, -1, document.left(), 120, cb);

            } catch (DocumentException e) {
                // Ignorieren - Footer ist optional
            }
        }

        private void addFooterCell(PdfPTable table, String text) {
            PdfPCell cell = new PdfPCell(new Phrase(text, fontSmall));
            cell.setBorder(Rectangle.NO_BORDER);
            cell.setPadding(3);
            cell.setVerticalAlignment(Element.ALIGN_TOP);
            table.addCell(cell);
        }
    }
}
