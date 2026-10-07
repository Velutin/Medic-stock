package com.project.mss.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.loan.LoanDTO;
import com.project.mss.dto.report.CancellationReportDTO;
import com.project.mss.dto.report.DeliveryReportDTO;
import com.project.mss.dto.report.PendingIssueReportDTO;
import com.project.mss.dto.report.ValidityReportDTO;
import com.project.mss.dto.report.WeeklyClosingDTO;
import com.project.mss.dto.stock.StockMovementDTO;
import com.project.mss.dto.surgery.SurgeryDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.model.entity.Hospital;
import com.project.mss.model.entity.PendingIssue;
import com.project.mss.model.entity.Stock;
import com.project.mss.model.entity.Surgery;
import com.project.mss.model.entity.SurgeryItem;
import com.project.mss.model.entity.User;
import com.project.mss.model.enums.LoanType;
import com.project.mss.model.enums.Location;
import com.project.mss.model.enums.MovementType;
import com.project.mss.model.enums.PendingIssueStatus;
import com.project.mss.model.enums.SurgeryStatus;
import com.project.mss.repository.DeliveryRepository;
import com.project.mss.repository.HospitalRepository;
import com.project.mss.repository.LoanRepository;
import com.project.mss.repository.PendingIssueRepository;
import com.project.mss.repository.StockMovementRepository;
import com.project.mss.repository.StockRepository;
import com.project.mss.repository.SurgeryRepository;
import com.project.mss.repository.UserRepository;

/**
 * Reports screen (ADMIN): each report as data for the on-screen preview and as a PDF with the same filters.
 * Periods are inclusive dates; surgery reports use the surgery date. hospitalId null means every allowed hospital.
 */
@Service
public class ReportQueryService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    /** Movements listed at most per report (the period should be narrowed beyond that). */
    private static final int MAX_MOVEMENTS = 3000;

    private final SurgeryRepository surgeryRepository;
    private final PendingIssueRepository pendingIssueRepository;
    private final DeliveryRepository deliveryRepository;
    private final LoanRepository loanRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockRepository stockRepository;
    private final HospitalRepository hospitalRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;
    private final PdfService pdfService;

    public ReportQueryService(SurgeryRepository surgeryRepository, PendingIssueRepository pendingIssueRepository,
                              DeliveryRepository deliveryRepository, LoanRepository loanRepository,
                              StockMovementRepository stockMovementRepository, StockRepository stockRepository,
                              HospitalRepository hospitalRepository, UserRepository userRepository,
                              AccessControlService accessControlService, PdfService pdfService) {
        this.surgeryRepository = surgeryRepository;
        this.pendingIssueRepository = pendingIssueRepository;
        this.deliveryRepository = deliveryRepository;
        this.loanRepository = loanRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.stockRepository = stockRepository;
        this.hospitalRepository = hospitalRepository;
        this.userRepository = userRepository;
        this.accessControlService = accessControlService;
        this.pdfService = pdfService;
    }

    /** Inclusive period; both dates are required by the screen, the defaults only protect direct calls. */
    public record Period(LocalDate start, LocalDate end) {
        public static Period of(LocalDate start, LocalDate end) {
            LocalDate e = end != null ? end : LocalDate.now();
            LocalDate s = start != null ? start : e.withDayOfMonth(1);
            if (s.isAfter(e)) throw new BusinessRuleException("The start date must be before the end date");
            return new Period(s, e);
        }
        LocalDateTime from() { return start.atStartOfDay(); }
        LocalDateTime to() { return end.atTime(LocalTime.MAX); }
        String label() { return start.format(DATE) + " a " + end.format(DATE); }
    }

    // ============================================================ weekly closing

    /** Week from Saturday to Friday containing the date: open and completed surgeries (cancelled ones do not count). */
    @Transactional(readOnly = true)
    public WeeklyClosingDTO weeklyClosing(LocalDate date, Long hospitalId) {
        accessControlService.requireManager();
        LocalDate start = ReportService.weekStart(date != null ? date : LocalDate.now());
        LocalDate end = start.plusDays(6);
        Map<Long, List<Surgery>> byHospital = new LinkedHashMap<>();
        surgeryRepository.listForReport(hospitals(hospitalId), start, end, List.of(SurgeryStatus.OPEN, SurgeryStatus.COMPLETED))
                .forEach(c -> byHospital.computeIfAbsent(c.getHospital().getId(), k -> new ArrayList<>()).add(c));
        List<User> techs = userRepository.findAll().stream()
                .filter(u -> u.isEnabled() && u.hasAnyRole("SURGICAL_TECH")).toList();

        List<WeeklyClosingDTO.HospitalRow> rows = new ArrayList<>();
        int totalSurgeries = 0;
        int totalItems = 0;
        for (List<Surgery> list : byHospital.values()) {
            Hospital h = list.get(0).getHospital();
            int items = list.stream().mapToInt(ReportQueryService::units).sum();
            List<String> names = techs.stream()
                    .filter(u -> u.getHospitals().stream().anyMatch(x -> x.getId().equals(h.getId())))
                    .map(User::getName).sorted().toList();
            rows.add(new WeeklyClosingDTO.HospitalRow(h.getId(), h.getName(), list.size(), items, names));
            totalSurgeries += list.size();
            totalItems += items;
        }
        rows.sort(Comparator.comparing(WeeklyClosingDTO.HospitalRow::hospital));
        return new WeeklyClosingDTO(start, end, end, totalSurgeries, totalItems, rows);
    }

    @Transactional(readOnly = true)
    public byte[] weeklyClosingPdf(LocalDate date, Long hospitalId) {
        WeeklyClosingDTO w = weeklyClosing(date, hospitalId);
        long techs = w.byHospital().stream().flatMap(r -> r.surgicalTechs().stream()).distinct().count();
        List<String[]> rows = w.byHospital().stream().map(r -> new String[]{r.hospital(), String.valueOf(r.surgeries()),
                String.valueOf(r.items()), r.surgicalTechs().isEmpty() ? "Nenhum vinculado" : String.join(", ", r.surgicalTechs())}).toList();
        return summary("CIRURGIAS DA SEMANA",
                "Sábado " + w.start().format(DATE) + " a sexta " + w.end().format(DATE) + " · pagamento na sexta · " + hospitalLabel(hospitalId),
                List.of(h(w.surgeries(), "cirurgias na semana"), h(w.items(), "itens consumidos"),
                        h(w.byHospital().size(), "hospitais"), h(techs, "instrumentadores")),
                List.of(table(null, null, null, new String[]{"HOSPITAL", "CIRURGIAS", "ITENS", "INSTRUMENTADORES"},
                        new float[]{2.2f, 0.9f, 0.8f, 3.4f}, new int[]{1, 2}, rows,
                        new String[]{"Total", String.valueOf(w.surgeries()), String.valueOf(w.items()), ""})),
                "Contam as cirurgias em aberto e concluídas, pela data da cirurgia. Canceladas ficam fora.");
    }

    // ============================================================ cancellations

    @Transactional(readOnly = true)
    public CancellationReportDTO cancellations(LocalDate start, LocalDate end, Long hospitalId) {
        accessControlService.requireManager();
        Period p = Period.of(start, end);
        List<Surgery> all = surgeryRepository.listForReport(hospitals(hospitalId), p.start(), p.end(),
                List.of(SurgeryStatus.values()));
        Map<String, int[]> byUser = new LinkedHashMap<>();
        List<CancellationReportDTO.Line> lines = new ArrayList<>();
        for (Surgery c : all) {
            String user = name(c.getCreatedBy());
            int[] counts = byUser.computeIfAbsent(user, k -> new int[2]);
            counts[0]++;
            if (c.getStatus() == SurgeryStatus.CANCELLED) {
                counts[1]++;
                lines.add(new CancellationReportDTO.Line(c.getId(), c.getSurgeryDate(), c.getHospital().getName(),
                        c.getPatientName(), user, c.getCancellationReason(), name(c.getCancelledBy()), c.getCancelledAt(),
                        c.getSheetFile() != null));
            }
        }
        List<CancellationReportDTO.UserRow> users = byUser.entrySet().stream()
                .map(e -> new CancellationReportDTO.UserRow(e.getKey(), e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingInt(CancellationReportDTO.UserRow::cancelled).reversed()
                        .thenComparing(CancellationReportDTO.UserRow::user))
                .toList();
        return new CancellationReportDTO(all.size(), lines.size(), users, lines);
    }

    @Transactional(readOnly = true)
    public byte[] cancellationsPdf(LocalDate start, LocalDate end, Long hospitalId) {
        CancellationReportDTO r = cancellations(start, end, hospitalId);
        List<String[]> users = r.byUser().stream().map(u -> new String[]{u.user(), String.valueOf(u.launched()),
                String.valueOf(u.cancelled()), percent(u.cancelled(), u.launched())}).toList();
        List<String[]> lines = r.surgeries().stream().map(l -> new String[]{l.surgeryDate().format(DATE), l.hospital(),
                l.patientName(), l.createdBy(), nvl(l.reason())}).toList();
        return summary("CANCELAMENTOS", Period.of(start, end).label() + " · " + hospitalLabel(hospitalId),
                List.of(h(r.launched(), "cirurgias lançadas"), h(r.cancelled(), "canceladas"),
                        new String[]{percent(r.cancelled(), r.launched()), "proporção cancelada"}, h(r.byUser().size(), "pessoas que lançaram")),
                List.of(table("Por quem lançou", null, "Proporção sobre o total lançado por cada pessoa",
                                new String[]{"LANÇADO POR", "LANÇADAS", "CANCELADAS", "PROPORÇÃO"}, new float[]{2.6f, 1f, 1f, 1f},
                                new int[]{1, 2, 3}, users, null),
                        table("Cirurgias canceladas", String.valueOf(lines.size()), null,
                                new String[]{"DATA", "HOSPITAL", "PACIENTE", "LANÇADO POR", "MOTIVO"}, new float[]{0.9f, 1.6f, 1.8f, 1.3f, 2.4f},
                                new int[]{}, lines, null)),
                "O período considera a data da cirurgia.");
    }

    // ============================================================ pending issues

    @Transactional(readOnly = true)
    public List<PendingIssueReportDTO> pendingIssues(LocalDate start, LocalDate end, Long hospitalId, PendingIssueStatus status) {
        accessControlService.requireManager();
        Period p = Period.of(start, end);
        return pendingIssueRepository.listForReport(hospitals(hospitalId), p.start(), p.end()).stream()
                .filter(i -> status == null || i.getStatus() == status)
                .map(ReportQueryService::pendingRow)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] pendingIssuesPdf(LocalDate start, LocalDate end, Long hospitalId, PendingIssueStatus status) {
        List<PendingIssueReportDTO> list = pendingIssues(start, end, hospitalId, status);
        List<String[]> rows = list.stream().map(i -> new String[]{i.surgeryDate().format(DATE), i.hospital(),
                i.enteredCode() + (i.enteredRef() != null ? " (REF " + i.enteredRef() + ")" : ""), reasonLabel(i.reason().name()),
                nvl(i.createdBy()), statusLabel(i.status()) + (i.resolution() != null ? ": " + i.resolution() : "")}).toList();
        return summary("PENDÊNCIAS", Period.of(start, end).label() + " · " + hospitalLabel(hospitalId)
                        + (status == null ? "" : " · " + statusLabel(status)),
                List.of(h(list.size(), "pendências"), h(count(list, PendingIssueStatus.OPEN), "em aberto"),
                        h(count(list, PendingIssueStatus.RESOLVED), "resolvidas"), h(count(list, PendingIssueStatus.DISCARDED), "descartadas")),
                List.of(table(null, null, null, new String[]{"DATA", "HOSPITAL", "ITEM LIDO", "PENDÊNCIA", "LANÇADO POR", "SITUAÇÃO"},
                        new float[]{0.9f, 1.5f, 1.8f, 1.5f, 1.2f, 2f}, new int[]{}, rows, null)),
                "Itens lidos na saída em cirurgia que não puderam ser lançados direto no estoque do hospital.");
    }

    // ============================================================ deliveries

    @Transactional(readOnly = true)
    public List<DeliveryReportDTO> deliveries(LocalDate start, LocalDate end, Long hospitalId) {
        accessControlService.requireManager();
        Period p = Period.of(start, end);
        Set<Long> allowed = hospitals(hospitalId);
        List<DeliveryReportDTO> out = new ArrayList<>();
        deliveryRepository.listForReport(allowed, p.from(), p.to()).forEach(d -> out.add(new DeliveryReportDTO(d.getId(),
                String.valueOf(d.getId()), false, "/deliveries/" + d.getId() + "/pdf", d.getCreatedAt(), d.getHospital().getName(),
                d.getSourceHospital() == null ? null : d.getSourceHospital().getName(), d.getItems().size(),
                d.getItems().stream().mapToInt(i -> i.getQuantity()).sum(), name(d.getCreatedBy()))));
        // Loans are delivered to the hospital as deliveries: they are listed here too, for auditing in one place
        loanRepository.listForReport(LoanType.LOAN, p.from(), p.to()).stream()
                .filter(l -> allowed.contains(l.getDestinationHospital().getId()) || allowed.contains(l.getSourceHospital().getId()))
                .forEach(l -> out.add(new DeliveryReportDTO(l.getId(), "E-" + l.getId(), true, "/loans/" + l.getId() + "/pdf",
                        l.getCreatedAt(), l.getDestinationHospital().getName(),
                        l.getSourceHospital().getName() + (l.getSourceLocation() == Location.STOREROOM ? " (sala)" : " (hospital)"),
                        l.getItems().size(), l.getItems().stream().mapToInt(i -> i.getQuantity()).sum(), name(l.getCreatedBy()))));
        out.sort(Comparator.comparing(DeliveryReportDTO::createdAt).reversed());
        return out;
    }

    // ============================================================ consumption

    /** Completed surgeries (cancelled ones go to the cancellations report), with items and table values. */
    @Transactional(readOnly = true)
    public List<SurgeryDTO> consumption(LocalDate start, LocalDate end, Long hospitalId, String patient) {
        accessControlService.requireManager();
        Period p = Period.of(start, end);
        String term = patient == null ? "" : patient.trim().toLowerCase(Locale.ROOT);
        return surgeryRepository.listForReport(hospitals(hospitalId), p.start(), p.end(), List.of(SurgeryStatus.COMPLETED))
                .stream()
                .filter(c -> term.isEmpty() || c.getPatientName().toLowerCase(Locale.ROOT).contains(term))
                .map(c -> SurgeryDTO.of(c, List.of(), true))
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] consumptionPdf(LocalDate start, LocalDate end, Long hospitalId, String patient) {
        List<SurgeryDTO> list = consumption(start, end, hospitalId, patient);
        BigDecimal total = BigDecimal.ZERO;
        int items = 0;
        List<PdfService.Section> sections = new ArrayList<>();
        for (SurgeryDTO c : list) {
            List<String[]> rows = new ArrayList<>();
            for (var i : c.items()) {
                String name = i.component() != null ? i.component() : i.description();
                rows.add(new String[]{name, i.ref(), i.lot(), String.valueOf(i.quantity()),
                        i.unitValue() == null ? "sem valor" : money(i.unitValue().multiply(BigDecimal.valueOf(i.quantity())))});
                items += i.quantity();
            }
            total = total.add(c.totalValue() == null ? BigDecimal.ZERO : c.totalValue());
            sections.add(table(c.patientName(), money(c.totalValue()),
                    c.hospital() + " · " + c.surgeryDate().format(DATE) + " · lançada por " + nvl(c.createdBy()),
                    new String[]{"MATERIAL", "REF", "LOTE", "QTD.", "VALOR"}, new float[]{2.6f, 1.4f, 1.1f, 0.5f, 1.2f},
                    new int[]{3, 4}, rows, null));
        }
        BigDecimal average = list.isEmpty() ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(list.size()), 2, java.math.RoundingMode.HALF_UP);
        String sub = Period.of(start, end).label() + " · " + hospitalLabel(hospitalId)
                + (patient == null || patient.isBlank() ? "" : " · paciente: " + patient.trim());
        return summary("CONSUMO POR CIRURGIA", sub,
                List.of(h(list.size(), "cirurgias concluídas"), h(items, "itens usados"),
                        new String[]{money(total), "valor total"}, new String[]{money(average), "média por cirurgia"}),
                sections.isEmpty() ? List.of(table(null, null, null, new String[]{"MATERIAL", "REF", "LOTE", "QTD.", "VALOR"},
                        new float[]{2.6f, 1.4f, 1.1f, 0.5f, 1.2f}, new int[]{3, 4}, List.of(), null)) : sections,
                "Só cirurgias concluídas, com os valores da tabela de cada hospital. Canceladas ficam em Cancelamentos.");
    }

    // ============================================================ validity

    /** Lots expired or expiring within the window (days), inside the hospitals and in the storerooms. */
    @Transactional(readOnly = true)
    public List<ValidityReportDTO> validity(Long hospitalId, int days) {
        accessControlService.requireManager();
        if (days < 0 || days > 365) throw new BusinessRuleException("Choose a window between 0 and 365 days");
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(days);
        List<ValidityReportDTO> out = new ArrayList<>();
        for (Long id : hospitals(hospitalId)) {
            for (Stock e : stockRepository.listByHospital(id)) {
                LocalDate expiry = e.getLot().getExpiryDate();
                if (e.getQuantity() <= 0 || expiry.isAfter(limit)) continue;
                out.add(new ValidityReportDTO(e.getHospital().getName(), e.getLocation(), e.getLot().getMaterial().getRef(),
                        e.getLot().getMaterial().getDescription(), e.getLot().getNumber(), expiry,
                        ChronoUnit.DAYS.between(today, expiry), e.getQuantity()));
            }
        }
        out.sort(Comparator.comparing(ValidityReportDTO::expiryDate).thenComparing(ValidityReportDTO::hospital));
        return out;
    }

    @Transactional(readOnly = true)
    public byte[] validityPdf(Long hospitalId, int days) {
        List<ValidityReportDTO> list = validity(hospitalId, days);
        List<String[]> rows = list.stream().map(v -> new String[]{v.hospital(),
                v.location() == Location.STOREROOM ? "Sala" : "Hospital", v.ref(), v.lot(), v.expiryDate().format(DATE),
                v.daysLeft() < 0 ? "Vencido" : v.daysLeft() + " dias", String.valueOf(v.quantity())}).toList();
        long expired = list.stream().filter(v -> v.daysLeft() < 0).count();
        int units = list.stream().mapToInt(ValidityReportDTO::quantity).sum();
        return summary("VALIDADE", hospitalLabel(hospitalId) + " · " + (days == 0 ? "só vencidos" : "vencidos e que vencem em até " + days + " dias"),
                List.of(h(list.size(), "lotes"), h(expired, "lotes vencidos"), h(list.size() - expired, "lotes vencendo"), h(units, "unidades")),
                List.of(table(null, null, null, new String[]{"HOSPITAL", "LOCAL", "REF", "LOTE", "VALIDADE", "SITUAÇÃO", "QTD."},
                        new float[]{1.8f, 0.8f, 1.3f, 1.1f, 1f, 1f, 0.6f}, new int[]{6}, rows, null)),
                null);
    }

    // ============================================================ loans and returns

    @Transactional(readOnly = true)
    public List<LoanDTO> loans(LoanType type, LocalDate start, LocalDate end, Long hospitalId) {
        accessControlService.requireManager();
        Period p = Period.of(start, end);
        Set<Long> allowed = hospitals(hospitalId);
        return loanRepository.listForReport(type, p.from(), p.to()).stream()
                .filter(e -> allowed.contains(e.getSourceHospital().getId())
                        || (e.getDestinationHospital() != null && allowed.contains(e.getDestinationHospital().getId())))
                .map(LoanDTO::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] loansPdf(LoanType type, LocalDate start, LocalDate end, Long hospitalId) {
        boolean returns = type == LoanType.RETURN;
        List<LoanDTO> list = loans(type, start, end, hospitalId);
        List<PdfService.Section> sections = new ArrayList<>();
        int units = 0;
        java.util.Set<String> lots = new java.util.HashSet<>();
        for (LoanDTO l : list) {
            List<String[]> rows = new ArrayList<>();
            int sum = 0;
            for (LoanDTO.LoanLine i : l.items()) {
                rows.add(new String[]{i.ref(), i.description(), i.lot(), i.expiryDate().format(DATE), String.valueOf(i.quantity())});
                sum += i.quantity();
                lots.add(i.ref() + "|" + i.lot());
            }
            units += sum;
            String from = l.sourceHospital() + (l.sourceLocation() == Location.STOREROOM ? " (sala)" : " (hospital)");
            String headingNote = l.createdAt().format(DATE) + " · " + from + (returns ? " · motivo: " + nvl(l.returnReason())
                    : " → " + l.destinationHospital()) + " · registrado por " + nvl(l.createdBy());
            sections.add(table((returns ? "Devolução nº " : "Empréstimo nº ") + l.id(), sum + (sum == 1 ? " peça" : " peças"),
                    headingNote, new String[]{"REF", "MATERIAL", "LOTE", "VALIDADE", "QTD."}, new float[]{1.3f, 2.6f, 1.1f, 1f, 0.5f},
                    new int[]{4}, rows, null));
        }
        return summary(returns ? "DEVOLUÇÕES À BAUMER" : "EMPRÉSTIMOS", Period.of(start, end).label() + " · " + hospitalLabel(hospitalId),
                List.of(h(list.size(), returns ? "devoluções" : "empréstimos"), h(units, "peças"), h(lots.size(), "lotes diferentes")),
                sections.isEmpty() ? List.of(table(null, null, null, new String[]{"REF", "MATERIAL", "LOTE", "VALIDADE", "QTD."},
                        new float[]{1.3f, 2.6f, 1.1f, 1f, 0.5f}, new int[]{4}, List.of(), null)) : sections,
                null);
    }

    // ============================================================ movements

    @Transactional(readOnly = true)
    public List<StockMovementDTO> movements(LocalDate start, LocalDate end, Long hospitalId, MovementType type) {
        accessControlService.requireManager();
        Period p = Period.of(start, end);
        return stockMovementRepository.listForReport(hospitals(hospitalId), p.from(), p.to(), PageRequest.of(0, MAX_MOVEMENTS))
                .stream()
                .filter(m -> type == null || m.getType() == type)
                .map(StockMovementDTO::of)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] movementsPdf(LocalDate start, LocalDate end, Long hospitalId, MovementType type) {
        List<StockMovementDTO> list = movements(start, end, hospitalId, type);
        List<String[]> rows = list.stream().map(m -> new String[]{
                m.date().format(DATE_TIME), movementLabel(m.type()), m.ref() + " · " + m.lot(), String.valueOf(m.quantity()),
                place(m.sourceHospital(), m.sourceLocation()), place(m.destinationHospital(), m.destinationLocation()),
                nvl(m.user())}).toList();
        return summary("MOVIMENTAÇÕES", Period.of(start, end).label() + " · " + hospitalLabel(hospitalId)
                        + " · " + (type == null ? "todos os tipos" : movementLabel(type)),
                List.of(h(list.size(), "movimentações"), h(countType(list, MovementType.ENTRY), "entradas"),
                        h(countType(list, MovementType.REPLENISHMENT), "transferências"),
                        h(countType(list, MovementType.SURGERY_WITHDRAWAL), "saídas em cirurgia")),
                List.of(table(null, null, null, new String[]{"DATA", "TIPO", "REF · LOTE", "QTD.", "ORIGEM", "DESTINO", "USUÁRIO"},
                        new float[]{1.2f, 1.3f, 1.8f, 0.5f, 1.6f, 1.6f, 1.1f}, new int[]{3}, rows, null)),
                list.size() >= MAX_MOVEMENTS ? "Mostrando as " + MAX_MOVEMENTS + " mais recentes: reduza o período para ver todas." : null);
    }

    // ============================================================ helpers

    private Set<Long> hospitals(Long hospitalId) {
        return hospitalId != null ? Set.of(accessControlService.requireHospitalAccess(hospitalId).getId())
                : accessControlService.allowedHospitals();
    }

    private String hospitalLabel(Long hospitalId) {
        return hospitalId == null ? "Todos" : hospitalRepository.findById(hospitalId).map(Hospital::getName).orElse("-");
    }

    private byte[] summary(String title, String subtitle, List<String[]> highlights, List<PdfService.Section> sections, String note) {
        return pdfService.summaryReport(new PdfService.SummaryReport(title, subtitle, highlights, sections, note));
    }

    private static PdfService.Section table(String heading, String headingValue, String headingNote, String[] columns,
                                            float[] widths, int[] rightAligned, List<String[]> rows, String[] total) {
        return new PdfService.Section(heading, headingValue, headingNote, columns, widths, rightAligned, rows, total);
    }

    private static String[] h(long value, String label) {
        return new String[]{String.valueOf(value), label};
    }

    private static long count(List<PendingIssueReportDTO> list, PendingIssueStatus status) {
        return list.stream().filter(i -> i.status() == status).count();
    }

    private static long countType(List<StockMovementDTO> list, MovementType type) {
        return list.stream().filter(m -> m.type() == type).count();
    }

    private static PendingIssueReportDTO pendingRow(PendingIssue i) {
        Surgery c = i.getSurgery();
        return new PendingIssueReportDTO(i.getId(), c.getId(), c.getSurgeryDate(), c.getPatientName(), i.getHospital().getId(),
                i.getHospital().getName(), i.getEnteredCode(), i.getEnteredRef(), i.getQuantity(), i.getReason(),
                name(c.getCreatedBy()), i.getStatus(), i.getResolution(), name(i.getResolvedBy()), i.getCreatedAt());
    }

    private static int units(Surgery c) {
        return c.getItems().stream().mapToInt(SurgeryItem::getQuantity).sum();
    }

    private static String name(User u) {
        return u == null ? "—" : u.getName();
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String percent(int part, int total) {
        return total == 0 ? "0%" : String.format(Locale.of("pt", "BR"), "%.1f%%", part * 100.0 / total);
    }

    private static String money(BigDecimal v) {
        return "R$ " + String.format(Locale.of("pt", "BR"), "%,.2f", v == null ? BigDecimal.ZERO : v);
    }

    private static String place(String hospital, Location location) {
        if (hospital == null) return "";
        return hospital + (location == Location.STOREROOM ? " (sala)" : location == Location.HOSPITAL ? " (hospital)" : "");
    }

    private static String statusLabel(PendingIssueStatus s) {
        return switch (s) {
            case OPEN -> "Em aberto";
            case RESOLVED -> "Resolvida";
            case DISCARDED -> "Descartada";
        };
    }

    private static String reasonLabel(String reason) {
        return switch (reason) {
            case "LOT_NOT_FOUND" -> "Lote não encontrado";
            case "AMBIGUOUS_LOT" -> "Lote com mais de uma validade";
            case "EXPIRED_LOT" -> "Lote vencido";
            case "NO_HOSPITAL_BALANCE" -> "Sem saldo no hospital";
            default -> reason;
        };
    }

    private static String movementLabel(MovementType t) {
        return switch (t) {
            case ENTRY -> "Entrada";
            case ENTRY_CORRECTION -> "Correção de entrada";
            case INVENTORY_ADJUSTMENT -> "Ajuste de inventário";
            case REPLENISHMENT -> "Transferência";
            case SURGERY_WITHDRAWAL -> "Saída em cirurgia";
            case SURGERY_REVERSAL -> "Estorno de cirurgia";
            case LOAN -> "Empréstimo";
            case SUPPLIER_RETURN -> "Devolução à Baumer";
        };
    }
}
