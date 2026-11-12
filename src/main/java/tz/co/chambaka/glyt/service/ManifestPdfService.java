package tz.co.chambaka.glyt.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tz.co.chambaka.glyt.domain.Bag;
import tz.co.chambaka.glyt.domain.Manifest;
import tz.co.chambaka.glyt.repo.ManifestBagRepository;
import tz.co.chambaka.glyt.repo.ManifestRepository;
import tz.co.chambaka.glyt.web.ApiException;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class ManifestPdfService {

    private final ManifestRepository manifests;
    private final ManifestBagRepository links;

    public ManifestPdfService(ManifestRepository manifests, ManifestBagRepository links) {
        this.manifests = manifests;
        this.links = links;
    }

    @Transactional(readOnly = true)
    public byte[] pdf(Long id) {
        Manifest manifest = manifests.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Manifest was not found."));
        List<Bag> bags = links.findByManifestId(id).stream().map(tz.co.chambaka.glyt.domain.ManifestBag::getBag).toList();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, out);
            document.open();
            Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(7, 28, 55));
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(51, 65, 85));
            Paragraph heading = new Paragraph("GoLyt airline manifest", title);
            heading.setAlignment(Element.ALIGN_LEFT);
            document.add(heading);
            document.add(new Paragraph(manifest.getNumber() + "  ·  " + manifest.getRoute() + "  ·  " + manifest.getAirline() + " " + manifest.getFlightNumber(), body));
            document.add(new Paragraph("Date " + manifest.getManifestDate() + "  ·  Status " + manifest.getStatus().name() + "  ·  Prepared by " + manifest.getPreparedBy().getFullName(), body));
            document.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(new float[]{2.2f, 3f, 1.4f, 2f, 2.2f});
            table.setWidthPercentage(100);
            header(table, "Bag");
            header(table, "Customer");
            header(table, "Weight");
            header(table, "Seal");
            header(table, "Status");
            for (Bag bag : bags) {
                cell(table, bag.getCode());
                cell(table, bag.getCustomer().getFullName());
                cell(table, String.format("%.1f kg", bag.getWeightKg()));
                cell(table, bag.getSealNumber() == null ? "" : bag.getSealNumber());
                cell(table, bag.getStatus().staffLabel());
            }
            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph(bags.size() + " bags  ·  " + String.format("%.1f kg", bags.stream().mapToDouble(Bag::getWeightKg).sum()), body));
            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The manifest could not be printed.");
        }
    }

    private void header(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
        cell.setBackgroundColor(new Color(7, 28, 55));
        cell.setPadding(6);
        table.addCell(cell);
    }

    private void cell(PdfPTable table, String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(51, 65, 85))));
        cell.setPadding(6);
        table.addCell(cell);
    }
}
