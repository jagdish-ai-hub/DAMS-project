package com.dams.invoiceextract;

import com.dams.common.exception.DamsException;
import com.dams.invoiceextract.dto.InvoiceExtractResponse;
import com.dams.invoiceextract.service.InvoiceExtractService;
import com.dams.invoiceextract.service.InvoiceExtractor;
import com.dams.invoiceextract.service.PdfTextReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceExtractServiceTest {

    @Mock private PdfTextReader reader;

    private InvoiceExtractService service() {
        return new InvoiceExtractService(reader, new InvoiceExtractor());
    }

    @Test
    void anEmptyUpload_isRefused() {
        MockMultipartFile empty = new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service().extract(empty)).isInstanceOf(DamsException.class).hasMessageContaining("Choose a PDF");
        verify(reader, never()).read(any());
    }

    @Test
    void aFileThatIsNotAPdf_isRefusedBeforeAnythingIsRun() {
        MockMultipartFile text = new MockMultipartFile("file", "a.pdf", "application/pdf", "hello, not a pdf".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service().extract(text)).isInstanceOf(DamsException.class).hasMessageContaining("not a PDF");
        verify(reader, never()).read(any());
    }

    @Test
    void aFileOverFiveMegabytes_isRefused() {
        byte[] big = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy("%PDF-".getBytes(StandardCharsets.ISO_8859_1), 0, big, 0, 5);
        MockMultipartFile file = new MockMultipartFile("file", "a.pdf", "application/pdf", big);

        assertThatThrownBy(() -> service().extract(file)).isInstanceOf(DamsException.class).hasMessageContaining("5 MB");
        verify(reader, never()).read(any());
    }

    @Test
    void aRealPdf_goesThroughTheReader_andComesBackExtracted() {
        byte[] pdf = "%PDF-1.4 anything".getBytes(StandardCharsets.ISO_8859_1);
        when(reader.read(pdf)).thenReturn(InvoiceExtractorTest.fixture("warranty_claim"));

        InvoiceExtractResponse r = service().extract(new MockMultipartFile("file", "claim.pdf", "application/pdf", pdf));

        assertThat(r.fileName()).isEqualTo("claim.pdf");
        assertThat(r.documentType()).isEqualTo("WARRANTY_CLAIM");
        assertThat(r.checksPassed()).isEqualTo(r.checksTotal());
    }
}
