package com.wg.banking.controller;

import com.itextpdf.text.DocumentException;
import com.wg.banking.service.PdfService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/pdf")
public class PdfController {
    @Autowired
    private PdfService pdfService;

    @GetMapping("/{accountId}")
    public ResponseEntity<Map<String, String>> getTransactionPdfUrl(@PathVariable String accountId) {
        try {
            String preSignedUrl = pdfService.generateAndUploadPdf(accountId);
            Map<String, String> response = Map.of("downloadUrl", preSignedUrl);
            return ResponseEntity.ok(response);
        } catch (DocumentException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}