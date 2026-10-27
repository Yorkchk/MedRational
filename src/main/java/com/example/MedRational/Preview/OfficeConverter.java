package com.example.MedRational.Preview;

import com.example.MedRational.Exceptions.PreviewUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.jodconverter.core.document.DefaultDocumentFormatRegistry;
import org.jodconverter.core.document.DocumentFormat;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

// Owns the LibreOffice process. If LibreOffice is not installed the app still starts;
// only Office -> PDF conversions fail (503), PDF previews keep working.
// preview.office-enabled=false skips starting it, e.g. in tests that boot the app: on Windows a
// test JVM can exit without stopping soffice, which then locks the profile for the next run.
@Slf4j
@Component
public class OfficeConverter implements InitializingBean, DisposableBean {

    private final boolean enabled;
    private final String officeHome;
    private final int portNumber;
    private final long taskExecutionTimeoutMs;

    private LocalOfficeManager officeManager;

    public OfficeConverter(@Value("${preview.office-enabled:true}") boolean enabled,
                           @Value("${preview.office-home:}") String officeHome,
                           @Value("${preview.port-numbers:2002}") int portNumber,
                           @Value("${preview.task-execution-timeout-ms:60000}") long taskExecutionTimeoutMs) {
        this.enabled = enabled;
        this.officeHome = officeHome;
        this.portNumber = portNumber;
        this.taskExecutionTimeoutMs = taskExecutionTimeoutMs;
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            log.info("LibreOffice is disabled (preview.office-enabled=false); Office document previews are off");
            return;
        }
        try {
            LocalOfficeManager.Builder builder = LocalOfficeManager.builder()
                    .portNumbers(portNumber)
                    .taskExecutionTimeout(taskExecutionTimeoutMs);
            if (officeHome != null && !officeHome.isBlank()) {
                builder.officeHome(officeHome);
            }
            LocalOfficeManager manager = builder.build();
            manager.start();
            officeManager = manager;
            log.info("LibreOffice started for document previews");
        } catch (Exception e) {
            log.warn("LibreOffice could not be started; Office document previews are disabled. "
                    + "Install LibreOffice and set LIBREOFFICE_HOME. Cause: {}", e.getMessage());
        }
    }

    @Override
    public void destroy() throws OfficeException {
        if (officeManager != null && officeManager.isRunning()) {
            officeManager.stop();
        }
    }

    public byte[] convertToPdf(byte[] source, DocumentFormat sourceFormat) {
        if (officeManager == null) {
            throw new PreviewUnavailableException("Document preview is not available on this server.");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            LocalConverter.make(officeManager)
                    .convert(new ByteArrayInputStream(source))
                    .as(sourceFormat)
                    .to(out)
                    .as(DefaultDocumentFormatRegistry.PDF)
                    .execute();
        } catch (OfficeException e) {
            throw new PreviewUnavailableException("Failed to generate document preview.", e);
        }
        return out.toByteArray();
    }
}
