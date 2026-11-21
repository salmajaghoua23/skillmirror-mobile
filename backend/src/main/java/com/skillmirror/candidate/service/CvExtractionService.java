package com.skillmirror.candidate.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import com.skillmirror.candidate.exception.CvProcessingException;
import java.io.ByteArrayInputStream;

@Service
public class CvExtractionService {

    public String extractText(byte[] content, String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase();
        String text;
        try {
            if (name.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(content)) {
                    text = new PDFTextStripper().getText(doc);
                }
            } else if (name.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content));
                     XWPFWordExtractor ex = new XWPFWordExtractor(doc)) {
                    text = ex.getText();
                }
            } else {
                throw new CvProcessingException("Format non supporté (PDF ou DOCX uniquement).");
            }
        } catch (CvProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new CvProcessingException("Fichier illisible ou corrompu.", e);
        }

        text = text.replace("\r", "").replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        if (text.length() < 50) {
            throw new CvProcessingException(
                    "Aucun texte exploitable (CV scanné en image ?). Importe un PDF ou DOCX texte.");
        }
        return text;
    }
}