package com.wg.banking.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.HttpMethod;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.wg.banking.model.Transaction;
import com.wg.banking.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Service
public class ReportService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AmazonS3 amazonS3Client;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    public String generateAndUploadPdf(String accountId) throws DocumentException {
        // Generate PDF content
        ByteArrayOutputStream pdfContent = generatePdfContent(accountId);

        // Generate unique filename
        String fileName = String.format("transactions_%s_%s.pdf", accountId, UUID.randomUUID().toString());
        String s3FilePath = accountId + "/" + fileName;

        // Upload to S3
        byte[] bytes = pdfContent.toByteArray();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("application/pdf");
        metadata.setContentLength(bytes.length);

        PutObjectRequest putObjectRequest = new PutObjectRequest(
                bucketName,
                s3FilePath,
                new ByteArrayInputStream(bytes),
                metadata
        );

        amazonS3Client.putObject(putObjectRequest);

        // Generate pre-signed URL (valid for 1 hour)
        URL preSignedUrl = getPreSignedUrl(s3FilePath);
        return preSignedUrl.toString();
    }

    private URL getPreSignedUrl(String s3FilePath) {
        Date expiration = new Date();
        long expTimeMillis = expiration.getTime();
        expTimeMillis += 1000 * 60 * 60; // 1 hour
        expiration.setTime(expTimeMillis);

        URL preSignedUrl = amazonS3Client.generatePresignedUrl(
                bucketName,
                s3FilePath,
                expiration,
                HttpMethod.GET
        );
        return preSignedUrl;
    }

    private ByteArrayOutputStream generatePdfContent(String accountId) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);

        document.open();

        // add header
        Font headerFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
        Paragraph header = new Paragraph("Transaction History", headerFont);
        header.setAlignment(Element.ALIGN_CENTER);
        document.add(header);
        document.add(Chunk.NEWLINE);

        // create table
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);

        // add table headers
        Stream.of("Timestamp", "Type", "Amount", "To")
                .forEach(columnTitle -> {
                    PdfPCell header1 = new PdfPCell();
                    header1.setBackgroundColor(BaseColor.LIGHT_GRAY);
                    header1.setBorderWidth(2);
                    header1.setPhrase(new Phrase(columnTitle));
                    table.addCell(header1);
                });

        // Add transactions.
        List<Transaction> transactions = transactionRepository.findAllByAccountId(accountId);
        for (Transaction transaction : transactions) {
            table.addCell(transaction.getCreatedAt().toString());
            table.addCell(transaction.getTransactionType().toString());
            table.addCell(String.format("$%.2f", transaction.getAmount()));
            table.addCell(transaction.getTargetAccount() == null ? "N/A" : transaction.getTargetAccount().getAccountNumber());
        }

        document.add(table);
        document.close();

        return out;
    }
}