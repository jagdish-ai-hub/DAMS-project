package com.dams.messaging;

import com.dams.common.exception.DamsException;
import com.dams.config.TenantContext;
import com.dams.messaging.dto.MessageLogResponse;
import com.dams.messaging.dto.SendMessageRequest;
import com.dams.messaging.entity.MessageLog;
import com.dams.messaging.entity.MessageTemplate;
import com.dams.messaging.repository.MessageLogRepository;
import com.dams.messaging.repository.MessageTemplateRepository;
import com.dams.messaging.service.MessageSender;
import com.dams.messaging.service.MessagingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FEAT-36: templated sends with {{placeholders}}, every attempt logged.
 * The default sender logs (LOGGED); a real provider flips rows to SENT.
 */
@ExtendWith(MockitoExtension.class)
class MessagingServiceTest {

    private static final long ORG = 1L;

    @Mock private MessageTemplateRepository templateRepo;
    @Mock private MessageLogRepository logRepo;
    @Mock private MessageSender sender;

    private MessagingService service;

    @BeforeEach
    void setUp() {
        service = new MessagingService(templateRepo, logRepo, sender);
        TenantContext.setOrgId(ORG);
        lenient().when(sender.providerName()).thenReturn("logging");
        lenient().when(logRepo.save(any(MessageLog.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void render_replacesPlaceholders_missingOnesRenderEmpty() {
        assertThat(MessagingService.render("Hi {{name}}, Rs.{{amount}} due {{dueDate}}",
            Map.of("name", "Sharma", "amount", "3133")))
            .isEqualTo("Hi Sharma, Rs.3133 due ");
        assertThat(MessagingService.render(null, null)).isEqualTo("");
    }

    @Test
    void send_rendersTemplate_logsAndRecordsLogged_withLoggingProvider() {
        when(templateRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of(template()));
        when(templateRepo.findByOrgIdAndCode(ORG, "due_reminder")).thenReturn(Optional.of(template()));

        MessageLogResponse r = service.send(request());

        assertThat(r.status()).isEqualTo("LOGGED");
        assertThat(r.body()).contains("Sharma Transport").contains("3133").doesNotContain("{{");
        assertThat(r.toPhone()).isEqualTo("9876543210");
        verify(sender).send(any(), any(), any(), any());
    }

    @Test
    void send_withRealProvider_recordsSent() {
        when(sender.providerName()).thenReturn("whatsapp-cloud");
        when(templateRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of(template()));
        when(templateRepo.findByOrgIdAndCode(ORG, "due_reminder")).thenReturn(Optional.of(template()));

        MessageLogResponse r = service.send(request());

        assertThat(r.status()).isEqualTo("SENT");
    }

    @Test
    void send_marksFailed_whenProviderThrows_butKeepsTheRow() {
        when(templateRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of(template()));
        when(templateRepo.findByOrgIdAndCode(ORG, "due_reminder")).thenReturn(Optional.of(template()));
        when(sender.send(any(), any(), any(), any())).thenThrow(new RuntimeException("provider down"));

        MessageLogResponse r = service.send(request());

        assertThat(r.status()).isEqualTo("FAILED");
        assertThat(r.error()).contains("provider down");
    }

    @Test
    void send_refusesWithoutAPhone_andDeactivatedTemplates() {
        when(templateRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of(template()));
        SendMessageRequest noPhone = request();
        noPhone.setToPhone("  ");
        assertThatThrownBy(() -> service.send(noPhone))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("phone");

        MessageTemplate off = template();
        off.setActive(false);
        when(templateRepo.findByOrgIdAndCode(ORG, "due_reminder")).thenReturn(Optional.of(off));
        assertThatThrownBy(() -> service.send(request()))
            .isInstanceOf(DamsException.class)
            .hasMessageContaining("deactivated");
    }

    @Test
    void templates_seedsDefaultsOnFirstRead() {
        when(templateRepo.findByOrgIdOrderByCodeAsc(ORG)).thenReturn(List.of());

        service.templates();

        // All four default templates seeded.
        verify(templateRepo, times(4)).save(any(MessageTemplate.class));
    }

    // ---------------------------------------------------------- fixtures

    private static SendMessageRequest request() {
        SendMessageRequest r = new SendMessageRequest();
        r.setTemplateCode("due_reminder");
        r.setToPhone("9876543210");
        r.setVariables(Map.of("name", "Sharma Transport", "amount", "3133",
            "docNo", "OOR-AUG26-R-005", "dueDate", "2026-09-01", "branch", "OOR"));
        r.setRelatedType("CreditFollowup");
        r.setRelatedId(900L);
        return r;
    }

    private static MessageTemplate template() {
        MessageTemplate t = new MessageTemplate();
        ReflectionTestUtils.setField(t, "id", 10L);
        t.setOrgId(ORG);
        t.setCode("due_reminder");
        t.setChannel("WHATSAPP");
        t.setBody("DAMS:Dear {{name}}, Rs.{{amount}} ({{docNo}}) was due on {{dueDate}}. {{branch}}");
        t.setActive(true);
        return t;
    }
}
