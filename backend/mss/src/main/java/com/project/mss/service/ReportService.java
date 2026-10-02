package com.project.mss.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.stock.StockRowDTO;
import com.project.mss.dto.report.WeeklySurgeriesDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.model.entity.Surgery;
import com.project.mss.model.entity.Loan;
import com.project.mss.model.entity.Delivery;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.SupplierOrder;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.SurgeryStatus;
import com.project.mss.repository.SurgeryRepository;

@Service
public class ReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PdfService pdfService;
    private final DeliveryService deliveryService;
    private final SupplierOrderService supplierOrderService;
    private final LoanService loanService;
    private final StockService stockService;
    private final SurgeryRepository surgeryRepository;
    private final AccessControlService accessControlService;

    public ReportService(PdfService pdfService, DeliveryService deliveryService,
                            SupplierOrderService supplierOrderService, LoanService loanService, StockService stockService,
                            SurgeryRepository surgeryRepository, AccessControlService accessControlService) {
        this.pdfService = pdfService;
        this.deliveryService = deliveryService;
        this.supplierOrderService = supplierOrderService;
        this.loanService = loanService;
        this.stockService = stockService;
        this.surgeryRepository = surgeryRepository;
        this.accessControlService = accessControlService;
    }

    // ============================================================ PDFs

    private static final String[] DELIVERY_COLUMNS = {"REF", "Material", "Lote", "Validade", "Qtd"};
    private static final float[] DELIVERY_WIDTHS = {1.4f, 4.2f, 1.6f, 1.1f, 0.6f};

    /**
     * Hospital delivery report, Classic layout. The material column uses the short name
     * (component) and falls back to the description. Deliveries from a distribution center
     * look the same: the material leaves the storeroom either way.
     */
    @Transactional(readOnly = true)
    public byte[] deliveryPdf(Long deliveryId) {
        Delivery e = deliveryService.load(deliveryId);
        List<String[]> rows = e.getItems().stream()
                .map(i -> {
                    var m = i.getLot().getMaterial();
                    String name = m.getComponent() != null && !m.getComponent().isBlank() ? m.getComponent() : m.getDescription();
                    return new String[]{m.getRef(), name, i.getLot().getNumber(), fmt(i.getLot().getExpiryDate()),
                            String.valueOf(i.getQuantity())};
                })
                .toList();
        int total = e.getItems().stream().mapToInt(i -> i.getQuantity()).sum();
        return pdfService.deliveryReport(new PdfService.DeliveryReport(
                String.valueOf(e.getId()),
                e.getHospital().getName(),
                fmt(e.getCreatedAt().toLocalDate()),
                e.getCreatedBy() != null ? e.getCreatedBy().getUsername() : "-",
                rows, total, e.getNotes()));
    }

    @Transactional(readOnly = true)
    public byte[] loanPdf(Long loanId) {
        Loan e = loanService.load(loanId);
        List<String[]> rows = e.getItems().stream()
                .map(i -> new String[]{i.getLot().getMaterial().getRef(), i.getLot().getMaterial().getDescription(),
                        i.getLot().getNumber(), fmt(i.getLot().getExpiryDate()), String.valueOf(i.getQuantity())})
                .toList();
        String sourceName = e.getSourceHospital().getName()
                + (e.getSourceLocation() == Location.STOREROOM ? " (material da sala)" : " (estoque do hospital)");
        return pdfService.generate(new PdfService.Report(
                "EMPRÉSTIMO DE MATERIAIS ENTRE HOSPITAIS",
                List.of(new String[]{"Empréstimo nº:", String.valueOf(e.getId())},
                        new String[]{"Origem:", sourceName},
                        new String[]{"Destino:", e.getDestinationHospital().getName()},
                        new String[]{"Data:", e.getCreatedAt().format(DATE_TIME)},
                        new String[]{"Observação:", e.getNotes()}),
                DELIVERY_COLUMNS, DELIVERY_WIDTHS, rows,
                "Total de itens: " + e.getItems().stream().mapToInt(i -> i.getQuantity()).sum(),
                new String[]{"Entregue por", "Recebido por (" + e.getDestinationHospital().getName() + ")"}));
    }

    /** Supplier order, to be sent via WhatsApp. */
    @Transactional(readOnly = true)
    public byte[] orderPdf(Long orderId) {
        SupplierOrder p = supplierOrderService.load(orderId);
        List<String[]> rows = p.getItems().stream()
                .sorted(Comparator.comparing((com.project.mss.model.entity.SupplierOrderItem i) -> !i.getUrgent())
                        .thenComparing(i -> i.getMaterial().getRef()))
                .map(i -> new String[]{i.getMaterial().getRef(), i.getMaterial().getDescription(),
                        String.valueOf(i.getQuantity()), Boolean.TRUE.equals(i.getUrgent()) ? "Sim" : "Não"})
                .toList();
        return pdfService.generate(new PdfService.Report(
                "SOLICITAÇÃO DE MATERIAL",
                List.of(new String[]{"Hospital:", p.getHospital().getName()},
                        new String[]{"Pedido nº:", String.valueOf(p.getId())},
                        new String[]{"Data:", p.getCreatedAt().format(DATE_TIME)},
                        new String[]{"Observação:", p.getNotes()}),
                new String[]{"REF", "Material", "Qtd", "Urgente"},
                new float[]{1.5f, 5f, 0.7f, 0.9f}, rows,
                "Total de itens: " + p.getItems().stream().mapToInt(i -> i.getQuantity()).sum(),
                new String[0]));
    }

    // ============================================================ pagamento semanal

    /**
     * Surgery count per week (Saturday to Friday, paid on Friday),
     * by hospital and by surgical tech. Cancelled surgeries are not counted.
     */
    @Transactional(readOnly = true)
    public List<WeeklySurgeriesDTO> surgeriesByWeek(LocalDate start, LocalDate end) {
        accessControlService.requireManager();
        LocalDate rangeStart = weekStart(start != null ? start : LocalDate.now());
        LocalDate f = weekStart(end != null ? end : LocalDate.now()).plusDays(6);
        if (rangeStart.plusYears(1).isBefore(f)) {
            throw new BusinessRuleException("The period must be at most 1 year");
        }

        List<Surgery> surgeries = surgeryRepository.listByPeriod(rangeStart, f,
                List.of(SurgeryStatus.OPEN, SurgeryStatus.COMPLETED));
        Map<LocalDate, List<Surgery>> byWeek = new TreeMap<>(surgeries.stream()
                .collect(Collectors.groupingBy(c -> weekStart(c.getSurgeryDate()))));

        List<WeeklySurgeriesDTO> weeks = new ArrayList<>();
        for (LocalDate s = rangeStart; !s.isAfter(f); s = s.plusWeeks(1)) {
            List<Surgery> list = byWeek.getOrDefault(s, List.of());
            weeks.add(new WeeklySurgeriesDTO(s, s.plusDays(6), s.plusDays(6), list.size(),
                    count(list, c -> c.getHospital().getName()),
                    count(list, c -> c.getSurgicalTech() != null ? c.getSurgicalTech().getUsername() : "(no surgical tech)")));
        }
        return weeks;
    }

    /** Saturday that opens the payment week containing the date. */
    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SATURDAY));
    }

    private List<WeeklySurgeriesDTO.Count> count(List<Surgery> list, Function<Surgery, String> key) {
        Map<String, Long> m = new HashMap<>();
        list.forEach(c -> m.merge(key.apply(c), 1L, Long::sum));
        return m.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new WeeklySurgeriesDTO.Count(e.getKey(), e.getValue()))
                .toList();
    }

    // ============================================================ stock spreadsheet

    /** Hospital stock as Excel, with the size identification color on each row. */
    @Transactional(readOnly = true)
    public byte[] stockSpreadsheet(Long hospitalId) {
        Hospital hospital = accessControlService.requireHospitalAccess(hospitalId);
        List<StockRowDTO> rows = stockService.hospitalView(hospitalId, false);

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sh = wb.createSheet("Estoque");
            String[] header = {"REF", "Descrição", "Componente", "Tamanho", "Lote", "Validade", "No hospital", "Na sala", "Total"};

            XSSFFont bold = wb.createFont();
            bold.setBold(true);
            XSSFCellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xD9, (byte) 0xD9, (byte) 0xD9}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            borders(headerStyle);

            Row title = sh.createRow(0);
            title.createCell(0).setCellValue("Estoque - " + hospital.getName() + " - " + LocalDate.now().format(DATE));
            title.getCell(0).setCellStyle(headerStyle);

            Row r = sh.createRow(2);
            for (int i = 0; i < header.length; i++) {
                r.createCell(i).setCellValue(header[i]);
                r.getCell(i).setCellStyle(headerStyle);
            }

            Map<String, XSSFCellStyle> stylesByColor = new HashMap<>();
            XSSFCellStyle defaultStyle = wb.createCellStyle();
            borders(defaultStyle);

            int n = 3;
            for (StockRowDTO l : rows) {
                XSSFCellStyle style = l.color() == null ? defaultStyle
                        : stylesByColor.computeIfAbsent(l.color(), color -> colorStyle(wb, color));
                Row row = sh.createRow(n++);
                Object[] v = {l.ref(), l.description(), l.component(), l.size(), l.lot(),
                        l.expiryDate().format(DATE),
                        l.hospitalQuantity(), l.storeroomQuantity(), l.hospitalQuantity() + l.storeroomQuantity()};
                for (int i = 0; i < v.length; i++) {
                    var cell = row.createCell(i);
                    if (v[i] instanceof Integer num) cell.setCellValue(num);
                    else cell.setCellValue(v[i] == null ? "" : v[i].toString());
                    cell.setCellStyle(style);
                }
            }
            for (int i = 0; i < header.length; i++) sh.autoSizeColumn(i);
            sh.createFreezePane(0, 3);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to generate spreadsheet: " + e.getMessage());
        }
    }

    private XSSFCellStyle colorStyle(XSSFWorkbook wb, String hex) {
        XSSFCellStyle s = wb.createCellStyle();
        borders(s);
        try {
            int rgb = Integer.parseInt(hex.replace("#", ""), 16);
            s.setFillForegroundColor(new XSSFColor(new byte[]{(byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb}, null));
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        } catch (NumberFormatException ignored) {
            // invalid color: leave unfilled
        }
        return s;
    }

    private void borders(CellStyle s) {
        s.setBorderBottom(BorderStyle.THIN);
        s.setBorderTop(BorderStyle.THIN);
        s.setBorderLeft(BorderStyle.THIN);
        s.setBorderRight(BorderStyle.THIN);
    }

    private static String fmt(LocalDate d) {
        return d == null ? "-" : d.format(DATE);
    }
}
