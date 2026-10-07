package com.project.mss.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.project.mss.dto.billing.BillingDTO;
import com.project.mss.dto.billing.BillingRateDTO;
import com.project.mss.dto.billing.BillingRateFormDTO;
import com.project.mss.exception.BusinessRuleException;
import com.project.mss.exception.EntityNotFoundException;
import com.project.mss.model.entity.BillingRate;
import com.project.mss.model.entity.Surgery;
import com.project.mss.model.entity.SurgeryItem;
import com.project.mss.repository.BillingRateRepository;
import com.project.mss.repository.SurgeryRepository;

/**
 * Billing of completed surgeries (by the month of the surgery date) and the configurable billing rates.
 * A rate applies from its start month until the next registered month. Rates can only be created,
 * changed or removed for the current or future months, so past billing never changes.
 */
@Service
public class BillingService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    private final BillingRateRepository billingRateRepository;
    private final SurgeryRepository surgeryRepository;
    private final AccessControlService accessControlService;
    private final PdfService pdfService;

    public BillingService(BillingRateRepository billingRateRepository, SurgeryRepository surgeryRepository,
                          AccessControlService accessControlService, PdfService pdfService) {
        this.billingRateRepository = billingRateRepository;
        this.surgeryRepository = surgeryRepository;
        this.accessControlService = accessControlService;
        this.pdfService = pdfService;
    }

    // ============================================================ rates

    @Transactional(readOnly = true)
    public List<BillingRateDTO> listRates() {
        accessControlService.requireManager();
        return billingRateRepository.findAllByOrderByValidFromDesc().stream().map(BillingRateDTO::of).toList();
    }

    /** Creates or changes the rates starting in the given month (current or future months only). */
    @Transactional
    public BillingRateDTO putRate(String month, BillingRateFormDTO dto) {
        accessControlService.requireManager();
        YearMonth start = parseMonth(month);
        requireNotPast(start);
        BillingRate rate = billingRateRepository.findByValidFrom(start.atDay(1)).orElseGet(() -> {
            BillingRate created = new BillingRate();
            created.setValidFrom(start.atDay(1));
            return created;
        });
        rate.setCommissionRate(dto.commissionRate());
        rate.setShareRate(dto.shareRate());
        rate.setCreatedBy(accessControlService.currentUser());
        return BillingRateDTO.of(billingRateRepository.save(rate));
    }

    /** Removes a rate of the current or a future month; the previous rate goes on applying. */
    @Transactional
    public void deleteRate(String month) {
        accessControlService.requireManager();
        YearMonth start = parseMonth(month);
        requireNotPast(start);
        BillingRate rate = billingRateRepository.findByValidFrom(start.atDay(1))
                .orElseThrow(() -> new EntityNotFoundException("No billing rate starts in " + start));
        billingRateRepository.delete(rate);
    }

    // ============================================================ billing

    /** Billing of the completed surgeries in the given months, for one hospital or every hospital. */
    @Transactional(readOnly = true)
    public BillingDTO billing(List<String> monthValues, Long hospitalId) {
        accessControlService.requireManager();
        TreeSet<YearMonth> months = parseMonths(monthValues);
        Set<Long> hospitals = hospitalId != null
                ? Set.of(accessControlService.requireHospitalAccess(hospitalId).getId())
                : accessControlService.allowedHospitals();

        List<Surgery> surgeries = hospitals.isEmpty() ? List.of() : surgeryRepository.listCompleted(hospitals,
                months.first().atDay(1), months.last().atEndOfMonth());
        Map<YearMonth, BillingRate> rates = new HashMap<>();

        List<BillingDTO.SurgeryBilling> rows = new ArrayList<>();
        Map<Long, HospitalAccumulator> byHospital = new LinkedHashMap<>();
        HospitalAccumulator total = new HospitalAccumulator(null, null, null);

        for (Surgery c : surgeries) {
            // Counted in the month of the surgery date, with the rates in effect in that month
            YearMonth month = YearMonth.from(c.getSurgeryDate());
            if (!months.contains(month)) continue;
            BillingRate rate = rates.computeIfAbsent(month, this::rateFor);

            int items = 0, withoutValue = 0;
            for (SurgeryItem i : c.getItems()) {
                items += i.getQuantity();
                if (i.getUnitValue() == null) withoutValue++;
            }
            BigDecimal value = c.getTotalValue() == null ? BigDecimal.ZERO : c.getTotalValue();
            BigDecimal commission = percent(value, rate.getCommissionRate());
            BigDecimal share = percent(commission, rate.getShareRate());

            rows.add(new BillingDTO.SurgeryBilling(c.getId(), c.getSurgeryDate(), c.getCompletedAt(), c.getPatientName(),
                    c.getHospital().getId(), c.getHospital().getName(),
                    c.getSurgicalTech() != null ? c.getSurgicalTech().getName() : null,
                    items, withoutValue, value, rate.getCommissionRate(), commission, rate.getShareRate(), share));

            byHospital.computeIfAbsent(c.getHospital().getId(), id -> new HospitalAccumulator(id,
                            c.getHospital().getName(),
                            c.getHospital().getPriceTableType() != null ? c.getHospital().getPriceTableType().name() : null))
                    .add(items, withoutValue, value, commission, share);
            total.add(items, withoutValue, value, commission, share);
        }

        BigDecimal average = total.surgeries == 0 ? BigDecimal.ZERO
                : total.value.divide(BigDecimal.valueOf(total.surgeries), 2, RoundingMode.HALF_UP);
        return new BillingDTO(List.copyOf(months), hospitalId, total.surgeries, total.items, total.withoutValue,
                total.value, total.commission, total.share, average,
                byHospital.values().stream().map(HospitalAccumulator::toDTO).toList(), rows);
    }

    /** Billing as a spreadsheet: summary by hospital and the surgeries of the period. */
    @Transactional(readOnly = true)
    public byte[] billingSpreadsheet(List<String> months, Long hospitalId) {
        BillingDTO b = billing(months, hospitalId);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet summary = wb.createSheet("Por hospital");
            header(summary, "Hospital", "Tabela", "Cirurgias", "Itens", "Itens sem valor", "Valor total", "Comissão",
                    "Sua parte da comissão");
            int r = 1;
            for (BillingDTO.HospitalBilling h : b.byHospital()) {
                Row row = summary.createRow(r++);
                row.createCell(0).setCellValue(h.hospital());
                row.createCell(1).setCellValue(h.priceTableType() == null ? "" : h.priceTableType());
                row.createCell(2).setCellValue(h.surgeryCount());
                row.createCell(3).setCellValue(h.itemCount());
                row.createCell(4).setCellValue(h.itemsWithoutValue());
                row.createCell(5).setCellValue(h.totalValue().doubleValue());
                row.createCell(6).setCellValue(h.commission().doubleValue());
                row.createCell(7).setCellValue(h.share().doubleValue());
            }
            Row totalRow = summary.createRow(r);
            totalRow.createCell(0).setCellValue("Total");
            totalRow.createCell(2).setCellValue(b.surgeryCount());
            totalRow.createCell(3).setCellValue(b.itemCount());
            totalRow.createCell(4).setCellValue(b.itemsWithoutValue());
            totalRow.createCell(5).setCellValue(b.totalValue().doubleValue());
            totalRow.createCell(6).setCellValue(b.commission().doubleValue());
            totalRow.createCell(7).setCellValue(b.share().doubleValue());

            Sheet detail = wb.createSheet("Cirurgias");
            header(detail, "Data da cirurgia", "Conclusão", "Paciente", "Hospital", "Instrumentador", "Itens",
                    "Itens sem valor", "Valor", "% comissão", "Comissão", "% sua parte", "Sua parte");
            r = 1;
            for (BillingDTO.SurgeryBilling s : b.surgeries()) {
                Row row = detail.createRow(r++);
                row.createCell(0).setCellValue(s.surgeryDate().format(DATE));
                row.createCell(1).setCellValue(s.completedAt().toLocalDate().format(DATE));
                row.createCell(2).setCellValue(s.patientName());
                row.createCell(3).setCellValue(s.hospital());
                row.createCell(4).setCellValue(s.surgicalTech() == null ? "" : s.surgicalTech());
                row.createCell(5).setCellValue(s.itemCount());
                row.createCell(6).setCellValue(s.itemsWithoutValue());
                row.createCell(7).setCellValue(s.totalValue().doubleValue());
                row.createCell(8).setCellValue(s.commissionRate().doubleValue());
                row.createCell(9).setCellValue(s.commission().doubleValue());
                row.createCell(10).setCellValue(s.shareRate().doubleValue());
                row.createCell(11).setCellValue(s.share().doubleValue());
            }
            for (int c = 0; c < 12; c++) {
                detail.autoSizeColumn(c);
                if (c < 8) summary.autoSizeColumn(c);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to generate spreadsheet: " + e.getMessage());
        }
    }

    /** Billing as PDF (document text in Portuguese). */
    @Transactional(readOnly = true)
    public byte[] billingPdf(List<String> months, Long hospitalId) {
        BillingDTO b = billing(months, hospitalId);
        String period = b.months().stream().map(m -> m.format(MONTH)).reduce((a, c) -> a + ", " + c).orElse("");
        String hospital = hospitalId == null ? "Todos os hospitais" : (b.byHospital().isEmpty() ? "-" : b.byHospital().get(0).hospital());
        List<String[]> byHospital = b.byHospital().stream().map(h -> new String[]{h.hospital(),
                h.priceTableType() == null ? "" : ("SIGTAP".equals(h.priceTableType()) ? "SIGTAP" : "Licitação"),
                String.valueOf(h.surgeryCount()), String.valueOf(h.itemCount()), money(h.totalValue()), money(h.commission()),
                money(h.share())}).toList();
        List<String[]> surgeries = b.surgeries().stream().map(s -> new String[]{s.surgeryDate().format(DATE), s.patientName(),
                s.hospital(), String.valueOf(s.itemCount()), money(s.totalValue()), money(s.commission()), money(s.share())}).toList();
        return pdfService.summaryReport(new PdfService.SummaryReport("FATURAMENTO", period + " · " + hospital,
                List.of(new String[]{money(b.totalValue()), b.surgeryCount() + " cirurgias concluídas"},
                        new String[]{money(b.commission()), "comissão sobre o total"},
                        new String[]{money(b.share()), "parte sobre a comissão"},
                        new String[]{money(b.averagePerSurgery()), "média por cirurgia"}),
                List.of(new PdfService.Section("Por hospital", null, null,
                                new String[]{"HOSPITAL", "TABELA", "CIRURGIAS", "ITENS", "VALOR TOTAL", "COMISSÃO", "PARTE"},
                                new float[]{2f, 0.9f, 0.9f, 0.6f, 1.3f, 1.2f, 1.1f}, new int[]{2, 3, 4, 5, 6}, byHospital,
                                new String[]{"Total", "", String.valueOf(b.surgeryCount()), String.valueOf(b.itemCount()),
                                        money(b.totalValue()), money(b.commission()), money(b.share())}),
                        new PdfService.Section("Cirurgias do período", String.valueOf(b.surgeryCount()), null,
                                new String[]{"DATA", "PACIENTE", "HOSPITAL", "ITENS", "VALOR", "COMISSÃO", "PARTE"},
                                new float[]{0.9f, 2.1f, 1.7f, 0.5f, 1.2f, 1.1f, 1f}, new int[]{3, 4, 5, 6}, surgeries, null)),
                b.itemsWithoutValue() > 0
                        ? b.itemsWithoutValue() + " itens sem valor na tabela do hospital ficaram fora dos totais."
                        : "Período pela data da cirurgia; só cirurgias concluídas, com os percentuais de cada mês."));
    }

    // ============================================================ helpers

    private BillingRate rateFor(YearMonth month) {
        return billingRateRepository.findFirstByValidFromLessThanEqualOrderByValidFromDesc(month.atDay(1))
                .orElseThrow(() -> new BusinessRuleException("No billing rate registered for " + month));
    }

    private static BigDecimal percent(BigDecimal base, BigDecimal rate) {
        return base.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new BusinessRuleException("Invalid month '" + value + "': use the format yyyy-MM, e.g. 2026-10");
        }
    }

    private static TreeSet<YearMonth> parseMonths(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return new TreeSet<>(Set.of(YearMonth.now()));
        }
        TreeSet<YearMonth> months = new TreeSet<>();
        values.forEach(v -> months.add(parseMonth(v.trim())));
        return months;
    }

    private static void requireNotPast(YearMonth month) {
        if (month.isBefore(YearMonth.now())) {
            throw new BusinessRuleException("Rates of past months cannot be changed (billing already calculated)");
        }
    }

    private static void header(Sheet sheet, String... titles) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < titles.length; i++) row.createCell(i).setCellValue(titles[i]);
    }

    private static String money(BigDecimal v) {
        return "R$ " + String.format(java.util.Locale.of("pt", "BR"), "%,.2f", v);
    }

    private static final class HospitalAccumulator {
        final Long id;
        final String name;
        final String table;
        int surgeries, items, withoutValue;
        BigDecimal value = BigDecimal.ZERO, commission = BigDecimal.ZERO, share = BigDecimal.ZERO;

        HospitalAccumulator(Long id, String name, String table) {
            this.id = id;
            this.name = name;
            this.table = table;
        }

        void add(int items, int withoutValue, BigDecimal value, BigDecimal commission, BigDecimal share) {
            this.surgeries++;
            this.items += items;
            this.withoutValue += withoutValue;
            this.value = this.value.add(value);
            this.commission = this.commission.add(commission);
            this.share = this.share.add(share);
        }

        BillingDTO.HospitalBilling toDTO() {
            return new BillingDTO.HospitalBilling(id, name, table, surgeries, items, withoutValue, value, commission, share);
        }
    }
}
