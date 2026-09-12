package com.tiendatech.ventas.presentation;

import com.tiendatech.ventas.application.FacturaService;
import com.tiendatech.ventas.infrastructure.experiment.ExperimentFaultInjector;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FacturaControllerTest {
    private final FacturaService facturaService = mock(FacturaService.class);
    private final ExperimentFaultInjector faultInjector = mock(ExperimentFaultInjector.class);
    private final FacturaController controller = new FacturaController(facturaService, faultInjector);

    @Test void descargarPdfDelegaAlServicioYFijaCabecerasDeDescarga() {
        byte[] pdfEsperado = "%PDF-1.4 contenido".getBytes();
        when(facturaService.generarPdf(9)).thenReturn(pdfEsperado);

        ResponseEntity<byte[]> respuesta = controller.descargarPdf(9);

        assertEquals(200, respuesta.getStatusCode().value());
        assertArrayEquals(pdfEsperado, respuesta.getBody());
        assertEquals(MediaType.APPLICATION_PDF, respuesta.getHeaders().getContentType());
        assertEquals("attachment; filename=factura-9.pdf", respuesta.getHeaders().getFirst("Content-Disposition"));
        verify(facturaService).generarPdf(9);
    }
}
