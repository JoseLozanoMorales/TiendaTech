package com.tiendatech.ventas.application;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import com.tiendatech.ventas.domain.Factura;
import com.tiendatech.ventas.domain.FacturaDetalle;
import com.tiendatech.ventas.domain.FacturaStore;
import com.tiendatech.ventas.domain.FacturaDraft;
import com.tiendatech.ventas.domain.FacturaOutboxStore;
import com.tiendatech.ventas.domain.InventarioPort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class FacturaService {

    private final FacturaStore facturaRepository;
    private final FacturaOutboxStore outboxRepository;
    private final InventarioPort inventarioClient;

    public FacturaService(FacturaStore facturaRepository,
                          FacturaOutboxStore outboxRepository,
                          InventarioPort inventarioClient) {
        this.facturaRepository = facturaRepository;
        this.outboxRepository = outboxRepository;
        this.inventarioClient = inventarioClient;
    }

    /**
     * Saga confirma la transaccion local de factura y deja Inventario en el
     * outbox durable. 2PC experimental conserva una barrera sincrona: el
     * checkout no se confirma hasta que Inventario acepta el segundo paso.
     * El outbox queda como recuperacion durable si esa llamada falla.
     */
    public Integer generar(FacturaDraft draft, CoordinationStrategy strategy) {
        Integer facturaId = facturaRepository.generar(draft);
        if (strategy == CoordinationStrategy.TWO_PHASE) {
            List<FacturaDetalle> detalle = facturaRepository.listarDetalle(facturaId);
            inventarioClient.registrarSalidasPorFactura(facturaId, detalle, "coord-2pc");
            outboxRepository.marcarProcesado(facturaId);
        }
        return facturaId;
    }

    public Factura obtenerPorId(Integer facturaId) {
        Factura factura = facturaRepository.obtenerPorId(facturaId);
        if (factura == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La factura " + facturaId + " no existe");
        }
        return factura;
    }

    public List<FacturaDetalle> listarDetalle(Integer facturaId) {
        obtenerPorId(facturaId); // valida que exista -> 404 si no
        return facturaRepository.listarDetalle(facturaId);
    }

    public List<Factura> listar(Integer usuarioId) {
        return facturaRepository.listar(usuarioId);
    }

    public List<Map<String, Object>> masVendidos(int limite) {
        return facturaRepository.masVendidos(Math.min(Math.max(limite, 1), 100));
    }

    public byte[] generarPdf(Integer facturaId) {
        Factura factura = obtenerPorId(facturaId);
        List<FacturaDetalle> detalle = listarDetalle(facturaId);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 40, 40, 50, 50);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            Font tituloFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10);
            Font totalFont = new Font(Font.HELVETICA, 12, Font.BOLD);

            documento.add(new Paragraph("TiendaTech - Factura " + factura.getNumero(), tituloFont));
            documento.add(new Paragraph(" "));
            documento.add(new Paragraph("Fecha de emision: " + factura.getFechaEmision(), normalFont));
            documento.add(new Paragraph("Cliente: " + factura.getNombre() + " (" + factura.getCedula() + ")", normalFont));
            documento.add(new Paragraph("Correo: " + factura.getCorreo(), normalFont));
            documento.add(new Paragraph("Direccion de entrega: " + factura.getDireccionEntrega(), normalFont));
            documento.add(new Paragraph(" "));

            PdfPTable tabla = new PdfPTable(5);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{3f, 1f, 1.2f, 1.2f, 1.2f});
            for (String encabezado : new String[]{"Producto", "Cant.", "Precio", "IVA", "Subtotal"}) {
                PdfPCell celda = new PdfPCell(new Phrase(encabezado, normalFont));
                celda.setBackgroundColor(Color.LIGHT_GRAY);
                tabla.addCell(celda);
            }
            for (FacturaDetalle linea : detalle) {
                tabla.addCell(new Phrase(linea.getNombreProducto(), normalFont));
                tabla.addCell(new Phrase(String.valueOf(linea.getCantidad()), normalFont));
                tabla.addCell(new Phrase(linea.getPrecio().toString(), normalFont));
                tabla.addCell(new Phrase(linea.getIva().toString(), normalFont));
                tabla.addCell(new Phrase(linea.getSubtotal().toString(), normalFont));
            }
            documento.add(tabla);

            documento.add(new Paragraph(" "));
            documento.add(new Paragraph("Subtotal: " + factura.getSubtotal(), normalFont));
            documento.add(new Paragraph("Total: " + factura.getTotal(), totalFont));
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF de la factura " + facturaId, e);
        } finally {
            documento.close();
        }
        return salida.toByteArray();
    }
}
