package com.dams.ai.controller;

import com.dams.ai.dto.AiAssistantDtos.AiAnswer;
import com.dams.ai.dto.AiAssistantDtos.AiBrief;
import com.dams.ai.dto.AiAssistantDtos.BenchmarkNarrative;
import com.dams.ai.dto.AiMastersDtos.LimitAdvice;
import com.dams.ai.dto.AiMastersDtos.MastersHealthItem;
import com.dams.ai.dto.AiMastersDtos.ReceiverDuplicate;
import com.dams.ai.dto.AiMastersDtos.SmartSearchResponse;
import com.dams.ai.dto.AiOpsDtos.CashAdvice;
import com.dams.ai.dto.AiOpsDtos.ClaimInsight;
import com.dams.ai.dto.AiOpsDtos.CloseChecklistRow;
import com.dams.ai.dto.AiWatchdogDtos.AnomalyItem;
import com.dams.ai.dto.AiWatchdogDtos.QueryRoot;
import com.dams.ai.dto.AiWatchdogDtos.RiskScore;
import com.dams.ai.service.AiAssistantService;
import com.dams.ai.service.AiMastersService;
import com.dams.ai.service.AiOpsService;
import com.dams.ai.service.AiSearchService;
import com.dams.ai.service.AiWatchdogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Owner/Admin AI assistant (FEAT-09..FEAT-21). Every endpoint is read-only over
 * the caller's own organisation — JWT {@code org_id} + branch scope apply exactly
 * like the screens they summarise, and the only write anywhere is the
 * {@code ai_query_log} trace row behind {@code POST /ai/ask}.
 */
@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI Assistant", description = "Owner/Admin insights over org-scoped aggregates")
@SecurityRequirement(name = "bearerAuth")
public class AiController {

    private final AiAssistantService assistantService;
    private final AiWatchdogService watchdogService;
    private final AiOpsService opsService;
    private final AiMastersService mastersService;
    private final AiSearchService searchService;

    public AiController(AiAssistantService assistantService,
                        AiWatchdogService watchdogService,
                        AiOpsService opsService,
                        AiMastersService mastersService,
                        AiSearchService searchService) {
        this.assistantService = assistantService;
        this.watchdogService = watchdogService;
        this.opsService = opsService;
        this.mastersService = mastersService;
        this.searchService = searchService;
    }

    public record AskRequest(
        @NotBlank(message = "Question must not be empty")
        @Size(max = 500, message = "Question is too long (max 500 characters)")
        String question,
        Long branchId
    ) {
    }

    @PostMapping("/ask")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
    @Operation(summary = "FEAT-09 Ask DAMS — grounded answer with cited doc numbers")
    public AiAnswer ask(@Valid @RequestBody AskRequest request) {
        return assistantService.ask(request.question(), request.branchId());
    }

    @GetMapping("/brief")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
    @Operation(summary = "FEAT-10 Morning Owner brief")
    public AiBrief brief(@RequestParam(required = false, defaultValue = "mtd") String period,
                         @RequestParam(required = false) Long branchId) {
        return assistantService.brief(period, branchId);
    }

    @GetMapping("/benchmark")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
    @Operation(summary = "FEAT-14 Branch benchmark narrative")
    public BenchmarkNarrative benchmark() {
        return assistantService.benchmark();
    }

    @GetMapping("/anomalies")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
    @Operation(summary = "FEAT-11 Anomaly and fraud watchdog flags")
    public List<AnomalyItem> anomalies(@RequestParam(required = false) Long branchId) {
        return watchdogService.anomalies(branchId);
    }

    @GetMapping("/risk")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER','ACCOUNTANT')")
    @Operation(summary = "FEAT-16 Document risk scores, riskiest first")
    public List<RiskScore> risk(@RequestParam(required = false, defaultValue = "receipt") String queue,
                                @RequestParam(required = false) Long branchId) {
        return watchdogService.riskScores(queue, branchId);
    }

    @GetMapping("/queries/roots")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
    @Operation(summary = "FEAT-13 Query root-cause clusters")
    public List<QueryRoot> queryRoots(@RequestParam(required = false) Long branchId) {
        return watchdogService.queryRoots(branchId);
    }

    @GetMapping("/claims/insights")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
    @Operation(summary = "FEAT-12 At-risk claim insights with follow-up drafts")
    public List<ClaimInsight> claimInsights(@RequestParam(required = false) Long branchId) {
        return opsService.claimInsights(branchId);
    }

    @GetMapping("/cash/advice")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
    @Operation(summary = "FEAT-15 Cash leak and deposit advice")
    public List<CashAdvice> cashAdvice(@RequestParam(required = false) Long branchId) {
        return opsService.cashAdvice(branchId);
    }

    @GetMapping("/close/checklist")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
    @Operation(summary = "FEAT-21 Month-end close checklist per branch")
    public List<CloseChecklistRow> closeChecklist(@RequestParam(required = false) Long branchId) {
        return opsService.closeChecklist(branchId);
    }

    @GetMapping("/receivers/duplicates")
    @PreAuthorize("hasAnyAuthority('OWNER','FINANCE_MANAGER')")
    @Operation(summary = "FEAT-17 Likely-duplicate vendors")
    public List<ReceiverDuplicate> receiverDuplicates() {
        return mastersService.receiverDuplicates();
    }

    @GetMapping("/masters/health")
    @PreAuthorize("hasAnyAuthority('OWNER')")
    @Operation(summary = "FEAT-18 Masters hygiene issues")
    public List<MastersHealthItem> mastersHealth() {
        return mastersService.mastersHealth();
    }

    @GetMapping("/limits/advice")
    @PreAuthorize("hasAnyAuthority('OWNER')")
    @Operation(summary = "FEAT-19 Expense limit revision hints")
    public List<LimitAdvice> limitAdvice() {
        return mastersService.limitAdvice();
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "FEAT-20 Smart search — tolerant query, ranked hits, same branch scope")
    public SmartSearchResponse smartSearch(@RequestParam(name = "q", required = false) String q) {
        return searchService.smartSearch(q);
    }
}
