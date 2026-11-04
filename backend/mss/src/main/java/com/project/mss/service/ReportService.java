package com.project.mss.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.loan.LoanDTO;
import com.project.mss.dto.replenishment.DeliveryDTO;
import com.project.mss.dto.stock.LotStockDTO;
import com.project.mss.dto.report.WeeklySurgeriesDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.model.entity.Surgery;
import com.project.mss.model.entity.Loan;
import com.project.mss.model.entity.Delivery;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.Lot;
import com.project.mss.model.entity.StoreroomReturn;
import com.project.mss.model.entity.SupplierOrder;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.SurgeryStatus;
import com.project.mss.repository.StockEntryItemInvoiceRepository;
import com.project.mss.repository.SurgeryRepository;

@Service
public class ReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PdfService pdfService;
    private final DeliveryService deliveryService;
    private final SupplierOrderService supplierOrderService;
    private final LoanService loanService;
    private final StoreroomReturnService storeroomReturnService;
    private final StockService stockService;
    private final SurgeryRepository surgeryRepository;
    private final StockEntryItemInvoiceRepository stockEntryItemInvoiceRepository;
    private final AccessControlService accessControlService;

    public ReportService(PdfService pdfService, DeliveryService deliveryService,
                            SupplierOrderService supplierOrderService, LoanService loanService,
                            StoreroomReturnService storeroomReturnService, StockService stockService,
                            SurgeryRepository surgeryRepository,
                            StockEntryItemInvoiceRepository stockEntryItemInvoiceRepository,
                            AccessControlService accessControlService) {
        this.pdfService = pdfService;
        this.deliveryService = deliveryService;
        this.supplierOrderService = supplierOrderService;
        this.loanService = loanService;
        this.storeroomReturnService = storeroomReturnService;
        this.stockService = stockService;
        this.surgeryRepository = surgeryRepository;
        this.stockEntryItemInvoiceRepository = stockEntryItemInvoiceRepository;
        this.accessControlService = accessControlService;
    }

    // ============================================================ PDFs


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
                    return new String[]{m.getRef(), name, Lot.printedNumber(i.getLot().getNumber()), fmt(i.getLot().getExpiryDate()),
                            String.valueOf(i.getQuantity())};
                })
                .toList();
        int total = e.getItems().stream().mapToInt(i -> i.getQuantity()).sum();
        return pdfService.deliveryReport(new PdfService.DeliveryReport(
                String.valueOf(e.getId()),
                e.getHospital().getName(),
                fmt(e.getCreatedAt().toLocalDate()),
                e.getCreatedBy() != null ? e.getCreatedBy().getName() : "-",
                rows, total, e.getNotes()));
    }

    @Transactional(readOnly = true)
    public byte[] loanPdf(Long loanId) {
        Loan e = loanService.load(loanId);
        if (e.getType() == com.project.mss.model.enums.LoanType.RETURN) return returnPdf(e);
        // The hospital receives a loan as a regular delivery: loan details are internal to the system
        return pdfService.classicReport(new PdfService.ClassicReport("ENTREGA DE MATERIAIS", "Entrega nº E-" + e.getId(),
                List.of(new String[]{"HOSPITAL", e.getDestinationHospital().getName()},
                        new String[]{"DATA DA ENTREGA", fmt(e.getCreatedAt().toLocalDate())},
                        new String[]{"ENTREGUE POR", e.getCreatedBy() != null ? e.getCreatedBy().getName() : "-"},
                        new String[]{"TOTAL DE PEÇAS", String.valueOf(pieces(e))}),
                new float[]{2.6f, 1.3f, 1.6f, 1.1f}, movementRows(e, false), pieces(e), null, null,
                new String[]{"Entregue por", "Recebido por (nome legível e data)"}));
    }

    /** Return to the supplier: the items sent back to Baumer, to send to the company. */
    private byte[] returnPdf(Loan e) {
        return pdfService.classicReport(new PdfService.ClassicReport("DEVOLUÇÃO DE MATERIAIS", "Devolução nº " + e.getId(),
                List.of(new String[]{"ORIGEM", sourceLabel(e)}, new String[]{"DESTINO", "Baumer"},
                        new String[]{"DATA DA DEVOLUÇÃO", fmt(e.getCreatedAt().toLocalDate())},
                        new String[]{"ENVIADO POR", e.getCreatedBy() != null ? e.getCreatedBy().getName() : "-"}),
                new float[]{2.2f, 1.1f, 1.4f, 1.6f, 1f}, movementRows(e, true), pieces(e), "Motivo", e.getReturnReason(),
                new String[]{"Enviado por", "Recebido por Baumer (nome legível e data)"}));
    }

    /** REF, material (short name), lot, expiry date and quantity; returns flag the lots expired on the return date. */
    private static List<String[]> movementRows(Loan e, boolean flagExpired) {
        LocalDate day = e.getCreatedAt().toLocalDate();
        return e.getItems().stream().map(i -> {
            var m = i.getLot().getMaterial();
            String name = m.getComponent() != null && !m.getComponent().isBlank() ? m.getComponent() : m.getDescription();
            String expiry = fmt(i.getLot().getExpiryDate()) + (flagExpired && i.getLot().isExpired(day) ? " (vencido)" : "");
            return new String[]{m.getRef(), name, Lot.printedNumber(i.getLot().getNumber()), expiry, String.valueOf(i.getQuantity())};
        }).toList();
    }

    private static String sourceLabel(Loan e) {
        return e.getSourceHospital().getName() + (e.getSourceLocation() == Location.STOREROOM ? " (sala)" : " (hospital)");
    }

    private static int pieces(Loan e) {
        return e.getItems().stream().mapToInt(i -> i.getQuantity()).sum();
    }

    /**
     * Withdrawal report: the material that left a hospital and went back into the storeroom.
     *
     * The signatures are the other way round from a delivery, because here it is the hospital that hands the
     * material over. Expired lots are flagged in the expiry column: they are a usual reason for a withdrawal,
     * and the paper has to say so plainly.
     */
    @Transactional(readOnly = true)
    public byte[] storeroomReturnPdf(Long returnId) {
        StoreroomReturn e = storeroomReturnService.load(returnId);
        LocalDate day = e.getCreatedAt().toLocalDate();
        List<String[]> rows = e.getItems().stream().map(i -> {
            var m = i.getLot().getMaterial();
            String name = m.getComponent() != null && !m.getComponent().isBlank() ? m.getComponent() : m.getDescription();
            String expiry = fmt(i.getLot().getExpiryDate()) + (i.getLot().isExpired(day) ? " (vencido)" : "");
            return new String[]{m.getRef(), name, Lot.printedNumber(i.getLot().getNumber()), expiry, String.valueOf(i.getQuantity())};
        }).toList();
        int total = e.getItems().stream().mapToInt(i -> i.getQuantity()).sum();
        return pdfService.classicReport(new PdfService.ClassicReport("RETIRADA DE MATERIAIS",
                "Retirada nº " + e.getId(),
                List.of(new String[]{"ORIGEM", e.getHospital().getName() + " (hospital)"},
                        new String[]{"DESTINO", e.getStoreroomHospital().getName() + " (sala)"},
                        new String[]{"DATA DA RETIRADA", fmt(day)},
                        new String[]{"RETIRADO POR", e.getCreatedBy() != null ? e.getCreatedBy().getName() : "-"}),
                new float[]{2.2f, 1.1f, 1.4f, 1.6f, 1f}, rows, total, "Motivo", e.getReason(),
                new String[]{"Entregue pelo hospital (nome legível e data)", "Retirado por"}));
    }

    /** Supplier order PDF. */
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
                    count(list, c -> c.getSurgicalTech() != null ? c.getSurgicalTech().getName() : "(no surgical tech)")));
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

    /**
     * Origin invoices of each lot, for the last column of the stock spreadsheets.
     *
     * This is the column that answers, months later, which invoice brought material that is no longer on
     * the shelf: the file keeps it even after the lot is gone. It is empty for everything that entered
     * before the invoices started being read, and for what entered without a note - an empty cell says
     * exactly that, instead of guessing an origin.
     */
    private Map<Long, String> invoicesByLot(List<Long> lotIds) {
        if (lotIds.isEmpty()) return Map.of();
        Map<Long, LinkedHashSet<String>> numbers = new HashMap<>();
        // In chunks: a whole-hospital export is thousands of lots, more than one IN list should carry
        for (int from = 0; from < lotIds.size(); from += INVOICE_LOOKUP_CHUNK) {
            List<Long> chunk = lotIds.subList(from, Math.min(from + INVOICE_LOOKUP_CHUNK, lotIds.size()));
            for (Object[] row : stockEntryItemInvoiceRepository.invoiceNumbersOfLots(chunk)) {
                numbers.computeIfAbsent((Long) row[0], k -> new LinkedHashSet<>()).add((String) row[1]);
            }
        }
        Map<Long, String> byLot = new HashMap<>();
        numbers.forEach((lotId, invoices) -> byLot.put(lotId, String.join(", ", invoices)));
        return byLot;
    }

    private static final int INVOICE_LOOKUP_CHUNK = 500;

    /**
     * Stock by lot as Excel, exactly what the screen is showing: the same search and the same hospital filter,
     * so the file and the tab never disagree. location HOSPITAL is the "by lot" tab (balance inside the
     * hospital, with the storeroom alongside); STOREROOM is the "in the storeroom" tab.
     *
     * The first columns keep the order of the spreadsheet this export replaces (REF to Total) and the sheet of
     * the hospital view keeps its name, so a formula pointing at a column or at the sheet still works; the new
     * columns come after. Expired lots are included - in the storeroom they are exactly what has to go back to
     * the supplier - and the expiry columns say which they are.
     */
    @Transactional(readOnly = true)
    public byte[] lotSpreadsheet(String term, Long hospitalId, Location location, boolean misplaced) {
        boolean storeroom = location == Location.STOREROOM;
        Page<LotStockDTO> page = stockService.lotsForExport(term, hospitalId, location, misplaced);
        List<LotStockDTO> rows = page.getContent();
        String place = hospitalId == null ? (storeroom ? "Todas as salas" : "Todos os hospitais")
                : accessControlService.requireHospitalAccess(hospitalId).getName();

        /*
         * The storeroom columns only exist for an administrator. For a read-only user the search already
         * comes back with the storeroom balance zeroed, and printing a "Na sala" column of zeros would
         * state that there is nothing in the storeroom - which is not what the zero means. Leaving the
         * columns out says the truth: this file is about what is inside the hospital.
         */
        boolean manager = accessControlService.isManager();
        boolean withStoreroom = manager;
        /*
         * The origin invoice is administrative information: which note brought the material in. A read-only
         * user has no business with it, so the column is left out for that profile - and the notes are not
         * even looked up, instead of being fetched and then dropped. Kept as its own flag, not as an alias of
         * withStoreroom: they happen to share the same rule today, and a change to one must not move the other.
         */
        boolean withInvoices = manager;
        Map<Long, String> invoices = withInvoices
                ? invoicesByLot(rows.stream().map(LotStockDTO::lotId).filter(java.util.Objects::nonNull).distinct().toList())
                : Map.of();

        String[] header;
        if (storeroom) {
            header = new String[]{"REF", "Descrição", "Componente", "Tamanho", "Lote", "Validade", "Na sala",
                                  "Situação", "Dias para vencer", "Sala", "Notas de origem"};
        } else if (withStoreroom) {
            header = new String[]{"REF", "Descrição", "Componente", "Tamanho", "Lote", "Validade", "No hospital",
                                  "Na sala", "Total", "Situação", "Dias para vencer", "Hospital", "Notas de origem"};
        } else {
            header = new String[]{"REF", "Descrição", "Componente", "Tamanho", "Lote", "Validade", "No hospital",
                                  "Situação", "Dias para vencer", "Hospital"};
        }

        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sh = wb.createSheet(storeroom ? "Estoque na sala" : "Estoque");

            XSSFFont bold = wb.createFont();
            bold.setBold(true);
            XSSFCellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFont(bold);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 0xD9, (byte) 0xD9, (byte) 0xD9}, null));
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            borders(headerStyle);

            // The title records what produced the file: where, the search used and, if it was cut, that it was
            StringBuilder titleText = new StringBuilder(storeroom
                    ? (misplaced ? "Saldo em sala de hospital atendido por centro de distribuição - " : "Estoque na sala - ")
                    : "Estoque por lote - ")
                    .append(place).append(" - ").append(LocalDate.now().format(DATE));
            if (term != null && !term.isBlank()) titleText.append(" - busca: ").append(term.trim());
            if (page.getTotalElements() > rows.size()) {
                titleText.append(" - mostrando as ").append(rows.size()).append(" primeiras de ")
                        .append(page.getTotalElements()).append(" linhas");
            }
            Row title = sh.createRow(0);
            title.createCell(0).setCellValue(titleText.toString());
            title.getCell(0).setCellStyle(headerStyle);

            Row head = sh.createRow(2);
            for (int i = 0; i < header.length; i++) {
                head.createCell(i).setCellValue(header[i]);
                head.getCell(i).setCellStyle(headerStyle);
            }

            Map<String, XSSFCellStyle> stylesByColor = new HashMap<>();
            XSSFCellStyle defaultStyle = wb.createCellStyle();
            borders(defaultStyle);

            LocalDate today = LocalDate.now();
            int n = 3;
            for (LotStockDTO l : rows) {
                long daysLeft = ChronoUnit.DAYS.between(today, l.expiryDate());
                String status = daysLeft < 0 ? "Vencido" : daysLeft <= 30 ? "Vence em até 30 dias" : "Válido";
                String origin = invoices.getOrDefault(l.lotId(), "");
                Object[] v;
                if (storeroom) {
                    v = new Object[]{l.ref(), l.description(), l.component(), l.size(), l.lot(),
                                     l.expiryDate().format(DATE), l.storeroomQuantity(), status, daysLeft,
                                     l.hospital(), origin};
                } else if (withStoreroom) {
                    v = new Object[]{l.ref(), l.description(), l.component(), l.size(), l.lot(),
                                     l.expiryDate().format(DATE), l.hospitalQuantity(), l.storeroomQuantity(),
                                     l.hospitalQuantity() + l.storeroomQuantity(), status, daysLeft,
                                     l.hospital(), origin};
                } else {
                    v = new Object[]{l.ref(), l.description(), l.component(), l.size(), l.lot(),
                                     l.expiryDate().format(DATE), l.hospitalQuantity(), status, daysLeft,
                                     l.hospital()};
                }
                XSSFCellStyle style = l.color() == null ? defaultStyle
                        : stylesByColor.computeIfAbsent(l.color(), color -> colorStyle(wb, color));
                Row row = sh.createRow(n++);
                for (int i = 0; i < v.length; i++) {
                    var cell = row.createCell(i);
                    if (v[i] instanceof Number num) cell.setCellValue(num.doubleValue());
                    else cell.setCellValue(v[i] == null ? "" : v[i].toString());
                    cell.setCellStyle(style);
                }
            }
            finish(sh, header.length);

            // One file, not two: the stock of this screen and what is still travelling towards it. The sheets
            // are added after "Estoque" and never touch its columns or its name, which is what his own
            // spreadsheet points its formulas at.
            if (!storeroom && manager) inTransitSheets(wb, headerStyle, term, hospitalId, place);

            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to generate spreadsheet: " + e.getMessage());
        }
    }

    /**
     * Two extra sheets of the stock-by-lot file: the balance on the road and the shipments that explain it.
     *
     * "A caminho" is the balance, lot by lot, and it is driven by the stock itself, because it is the number
     * that has to add up: total of "Estoque" plus total of "A caminho" is everything the operation owns.
     * "Remessas" says where that balance came from and for how long it has been travelling.
     *
     * The two totals can differ, and the difference is the point: units dispatched that did not arrive and
     * are waiting for a decision belong to a shipment already confirmed, so they appear in the balance and
     * not among the open shipments. Sheets built only from the shipments would leave out exactly what a
     * count is hunting for. The line under each total says this in Portuguese, on the file, because whoever
     * opens it in six months will not have this conversation at hand.
     *
     * Both sheets honour the same search as "Estoque". A file whose sheets disagree about what was searched
     * would be worse than no file at all.
     */
    private void inTransitSheets(XSSFWorkbook wb, XSSFCellStyle headerStyle, String term, Long hospitalId,
                                 String place) {
        XSSFCellStyle cell = wb.createCellStyle();
        borders(cell);
        LocalDate today = LocalDate.now();
        String searched = term == null || term.isBlank() ? "" : " - busca: " + term.trim();

        List<LotStockDTO> balance = stockService.inTransitLots(hospitalId, term);
        XSSFSheet sh = wb.createSheet("A caminho");
        long units = balance.stream().mapToLong(LotStockDTO::inTransitQuantity).sum();
        sheetTitle(sh, headerStyle, "Material a caminho - " + place + " - " + today.format(DATE) + searched
                + " - " + balance.size() + (balance.size() == 1 ? " linha" : " linhas") + ", " + units
                + (units == 1 ? " peça" : " peças"));
        String[] header = {"REF", "Descrição", "Componente", "Tamanho", "Lote", "Validade", "Situação",
                           "Dias para vencer", "A caminho", "Hospital de destino"};
        writeHeader(sh, headerStyle, header);
        int n = 3;
        for (LotStockDTO l : balance) {
            long daysLeft = ChronoUnit.DAYS.between(today, l.expiryDate());
            String status = daysLeft < 0 ? "Vencido" : daysLeft <= 30 ? "Vence em até 30 dias" : "Válido";
            writeRow(sh, n++, cell, new Object[]{l.ref(), l.description(), l.component(), l.size(),
                    Lot.printedNumber(l.lot()), l.expiryDate().format(DATE), status, daysLeft,
                    l.inTransitQuantity(), l.hospital()});
        }
        writeRow(sh, n + 1, cell, new Object[]{"Total de peças a caminho", units});
        writeRow(sh, n + 2, cell, new Object[]{"Estoque total = total da coluna \"Total\" da aba Estoque + este "
                + "total. Inclui peças de remessas já confirmadas que não chegaram e aguardam decisão na tela "
                + "de Pendências."});
        finish(sh, header.length);

        List<DeliveryDTO> transfers = deliveryService.listDispatched().stream()
                .filter(d -> hospitalId == null || hospitalId.equals(d.hospitalId()))
                .toList();
        List<LoanDTO> loans = loanService.listDispatched().stream()
                .filter(l -> hospitalId == null || hospitalId.equals(l.destinationHospitalId()))
                .toList();
        XSSFSheet sh2 = wb.createSheet("Remessas");
        String[] header2 = {"Remessa", "Tipo", "Origem", "Destino", "Despachada em", "Dias na estrada",
                            "REF", "Descrição", "Lote", "Validade", "Qtd. despachada"};
        writeHeader(sh2, headerStyle, header2);
        int n2 = 3;
        long sent = 0;
        for (DeliveryDTO d : transfers) {
            String origin = (d.sourceHospital() != null ? d.sourceHospital() : d.hospital()) + " (sala)";
            for (DeliveryDTO.DeliveryLine i : d.items()) {
                sent += i.quantity();
                writeRow(sh2, n2++, cell, new Object[]{"nº " + d.id(), "Transferência", origin, d.hospital(),
                        d.createdAt().toLocalDate().format(DATE),
                        ChronoUnit.DAYS.between(d.createdAt().toLocalDate(), today), i.ref(), i.description(),
                        Lot.printedNumber(i.lot()), i.expiryDate().format(DATE), i.quantity()});
            }
        }
        for (LoanDTO l : loans) {
            String origin = l.sourceHospital() + (l.sourceLocation() == Location.STOREROOM ? " (sala)" : " (hospital)");
            for (LoanDTO.LoanLine i : l.items()) {
                sent += i.quantity();
                writeRow(sh2, n2++, cell, new Object[]{"nº " + l.id(), "Empréstimo", origin,
                        l.destinationHospital(), l.createdAt().toLocalDate().format(DATE),
                        ChronoUnit.DAYS.between(l.createdAt().toLocalDate(), today), i.ref(), i.description(),
                        Lot.printedNumber(i.lot()), i.expiryDate().format(DATE), i.quantity()});
            }
        }
        sheetTitle(sh2, headerStyle, "Remessas despachadas e não confirmadas - " + place + " - "
                + today.format(DATE) + " - " + (transfers.size() + loans.size())
                + (transfers.size() + loans.size() == 1 ? " remessa, " : " remessas, ") + sent
                + (sent == 1 ? " peça" : " peças"));
        writeRow(sh2, n2 + 1, cell, new Object[]{"Total despachado e não confirmado", sent});
        writeRow(sh2, n2 + 2, cell, new Object[]{"Diferença para a aba \"A caminho\": " + (units - sent)
                + ". Diferente de zero significa peça de remessa já confirmada que não chegou e aguarda "
                + "decisão na tela de Pendências."});
        finish(sh2, header2.length);
    }

    private void sheetTitle(XSSFSheet sh, XSSFCellStyle style, String text) {
        Row title = sh.createRow(0);
        title.createCell(0).setCellValue(text);
        title.getCell(0).setCellStyle(style);
    }

    private void writeHeader(XSSFSheet sh, XSSFCellStyle style, String[] header) {
        Row head = sh.createRow(2);
        for (int i = 0; i < header.length; i++) {
            head.createCell(i).setCellValue(header[i]);
            head.getCell(i).setCellStyle(style);
        }
    }

    private void writeRow(XSSFSheet sh, int index, XSSFCellStyle style, Object[] values) {
        Row row = sh.createRow(index);
        for (int i = 0; i < values.length; i++) {
            var c = row.createCell(i);
            if (values[i] instanceof Number num) c.setCellValue(num.doubleValue());
            else c.setCellValue(values[i] == null ? "" : values[i].toString());
            c.setCellStyle(style);
        }
    }

    private void finish(XSSFSheet sh, int columns) {
        for (int i = 0; i < columns; i++) sh.autoSizeColumn(i);
        sh.createFreezePane(0, 3);
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
