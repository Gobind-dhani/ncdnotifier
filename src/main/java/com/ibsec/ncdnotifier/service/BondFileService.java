package com.ibsec.ncdnotifier.service;

import org.apache.commons.csv.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.Locale;

@Service
public class BondFileService {

    private static final Logger log = LoggerFactory.getLogger(BondFileService.class);

    private static final List<String> FILE_URLS = List.of(
              "https://nsearchives.nseindia.com/content/debt/Corporate_bond_report_14-Nov-2025.csv",
            "https://www.nseindia.com/api/reports?archives=%5B%7B%22name%22%3A%22Approved%20list%20of%20GSEC%20and%20TBILL%20(.zip)%22%2C%22type%22%3A%22monthly-reports%22%2C%22category%22%3A%22debt%22%2C%22section%22%3A%22nse-ebp%22%7D%5D&type=nse-ebp&mode=single"
    );

    public List<BondRecord> fetchAllBonds() {
        Set<BondRecord> allBonds = new HashSet<>();

        for (String fileUrl : FILE_URLS) {
            try (InputStream in = openConnectionWithHeaders(fileUrl)) {

                if (in == null) {
                    log.warn("No input stream for {}", fileUrl);
                    continue;
                }

                if (fileUrl.contains("api/reports") || fileUrl.endsWith(".zip")) {
                    allBonds.addAll(parseZipFile(in));
                } else {
                    allBonds.addAll(parseCsvStream(in));
                }

            } catch (Exception e) {
                log.error("Error processing file from {}", fileUrl, e);
            }
        }

        log.info("Fetched {} bond records", allBonds.size());
        return new ArrayList<>(allBonds);
    }

    private InputStream openConnectionWithHeaders(String fileUrl) throws IOException {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Accept", "*/*");

        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);

        int code = connection.getResponseCode();
        log.info("Response {} -> {}", fileUrl, code);

        return code == 200 ? connection.getInputStream() : null;
    }

    // ============================================================
    // ZIP PARSER - safe per-entry reads
    // ============================================================
    private List<BondRecord> parseZipFile(InputStream zipStream) throws IOException {
        List<BondRecord> list = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                log.info("Processing ZIP entry: {}", name);

                if (name.toLowerCase().endsWith(".csv")) {
                    // read CSV entry bytes and parse
                    byte[] csvBytes = readEntryBytes(zis);
                    zis.closeEntry();
                    try (InputStream csvIn = new ByteArrayInputStream(csvBytes)) {
                        list.addAll(parseCsvStream(csvIn));
                    }
                } else if (name.toLowerCase().endsWith(".xlsx")) {
                    // read XLSX entry bytes into memory and parse from bytes (safe)
                    byte[] xlsxBytes = readEntryBytes(zis);
                    zis.closeEntry();
                    list.addAll(parseXlsxFromBytes(xlsxBytes));
                } else {
                    // skip other file types
                    zis.closeEntry();
                }
            }
        }

        return list;
    }

    private byte[] readEntryBytes(ZipInputStream zis) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = zis.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }

    // ============================================================
    // XLSX PARSER (pure-XML, no POI) - read from byte[]
    // ============================================================
    private List<BondRecord> parseXlsxFromBytes(byte[] xlsxData) {
        List<BondRecord> list = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(xlsxData))) {

            Map<Integer, String> sharedStrings = new HashMap<>();
            String sheetXml = null;

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String en = entry.getName();

                if ("xl/sharedStrings.xml".equals(en)) {
                    byte[] ss = readEntryBytes(zis);
                    zis.closeEntry();
                    sharedStrings = readSharedStrings(new ByteArrayInputStream(ss));
                } else if (en.startsWith("xl/worksheets/") && en.endsWith(".xml")) {
                    byte[] s = readEntryBytes(zis);
                    zis.closeEntry();
                    // pick the first worksheet encountered
                    if (sheetXml == null) sheetXml = new String(s);
                } else {
                    zis.closeEntry();
                }
            }

            if (sheetXml == null) {
                log.error("Sheet XML not found in XLSX");
                return list;
            }

            // parse sheet xml using sharedStrings map
            list.addAll(parseSheetXml(sheetXml, sharedStrings));

        } catch (Exception e) {
            log.error("Error parsing XLSX manually", e);
        }

        return list;
    }

    private Map<Integer, String> readSharedStrings(InputStream is) throws Exception {
        Map<Integer, String> map = new HashMap<>();

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(is);

        NodeList siNodes = doc.getElementsByTagName("si");
        int idx = 0;
        for (int i = 0; i < siNodes.getLength(); i++) {
            Node si = siNodes.item(i);
            // extract concatenated text of all <t> inside <si>
            String text = si.getTextContent();
            map.put(idx++, text);
        }

        return map;
    }

    private List<BondRecord> parseSheetXml(String xml, Map<Integer, String> shared) throws Exception {
        List<BondRecord> list = new ArrayList<>();

        Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes()));

        NodeList rowNodes = doc.getElementsByTagName("row");

        int headerRowIndex = -1;
        Map<Integer, String> headerIndex = new HashMap<>();

        for (int r = 0; r < rowNodes.getLength(); r++) {
            Element row = (Element) rowNodes.item(r);
            NodeList cellNodes = row.getElementsByTagName("c");

            List<String> cellValues = new ArrayList<>();

            for (int c = 0; c < cellNodes.getLength(); c++) {
                Element cell = (Element) cellNodes.item(c);
                String type = cell.getAttribute("t"); // may be "s" for shared string or "inlineStr"

                // prefer <v>, fallback to inline <is><t> or <t>
                Node vNode = getFirstChildByName(cell, "v");
                Node tNode = getFirstChildByName(cell, "t"); // inline text location
                String value = "";

                if (vNode != null) {
                    value = vNode.getTextContent();

                    if ("s".equals(type)) {
                        try {
                            int sidx = Integer.parseInt(value);
                            value = shared.getOrDefault(sidx, "");
                        } catch (NumberFormatException ex) {
                            // leave value as-is
                        }
                    } else {
                        // numeric or other non-shared value: might be Excel numeric date serial
                        // detect numeric (integer or decimal)
                        if (value != null && value.matches("^-?\\d+(\\.\\d+)?$")) {
                            try {
                                double serial = Double.parseDouble(value);
                                // Excel date serials count days from 1899-12-30
                                long days = (long) Math.floor(serial);
                                LocalDate excelDate = LocalDate.of(1899, 12, 30).plusDays(days);
                                value = excelDate.toString(); // yyyy-MM-dd
                            } catch (Exception ignored) {
                                // if parsing fails, keep raw numeric as string
                            }
                        }
                    }
                } else if (tNode != null) {
                    value = tNode.getTextContent();
                } else {
                    // empty cell
                    value = "";
                }

                cellValues.add(value == null ? "" : value.trim());
            }

            // detect header row (first non-empty row containing "ISIN")
            if (headerRowIndex == -1) {
                boolean containsIsin = cellValues.stream()
                        .anyMatch(s -> s != null && s.toString().trim().equalsIgnoreCase("ISIN"));
                if (containsIsin) {
                    headerRowIndex = r;
                    for (int c = 0; c < cellValues.size(); c++) {
                        headerIndex.put(c, cellValues.get(c).trim());
                    }
                    // header consumed; continue to next rows for data
                    continue;
                } else {
                    // not header, continue scanning next row
                    continue;
                }
            }

            // if headerRowIndex found, process subsequent rows as data
            if (headerRowIndex != -1 && headerIndex.size() > 0) {
                String isin = getByHeader(headerIndex, cellValues, "ISIN");
                String name = getByHeader(headerIndex, cellValues, "Security Description");
                String maturity = getByHeader(headerIndex, cellValues, "Symbol / Maturity Date");

                if (isin == null || isin.isBlank()) continue;

                list.add(new BondRecord(isin, name, parseDate(maturity)));
            }
        }

        log.info("Parsed {} rows from XLSX sheet", list.size());
        return list;
    }

    private Node getFirstChildByName(Node node, String name) {
        NodeList nl = node.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i).getNodeName().equals(name)) return nl.item(i);
        }
        return null;
    }

    private String getByHeader(Map<Integer, String> headers, List<String> row, String headerName) {
        for (Map.Entry<Integer, String> e : headers.entrySet()) {
            if (e.getValue() != null && e.getValue().equalsIgnoreCase(headerName)) {
                int idx = e.getKey();
                return idx < row.size() ? row.get(idx) : "";
            }
        }
        return "";
    }

    // ============================================================
    // CSV PARSER (robust header detection + safe join)
    // ============================================================
    private List<BondRecord> parseCsvStream(InputStream inputStream) throws IOException {
        List<BondRecord> bonds = new ArrayList<>();

        // Read all lines first (safe) and find header line that contains ISIN
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            List<String> lines = reader.lines().collect(Collectors.toList());
            if (lines.isEmpty()) {
                log.warn("CSV stream empty");
                return bonds;
            }

            int headerIdx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).toUpperCase().contains("ISIN")) {
                    headerIdx = i;
                    break;
                }
            }

            if (headerIdx == -1) {
                log.warn("No CSV header found containing 'ISIN'.");
                return bonds;
            }

            String csvContent = lines.subList(headerIdx, lines.size()).stream().collect(Collectors.joining("\n"));

            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withTrim()
                    .withIgnoreSurroundingSpaces()
                    .parse(new StringReader(csvContent));

            Map<String, Integer> headerMap = parser.getHeaderMap();

            // dynamic header detection
            String nameHeader = findHeader(headerMap.keySet(), List.of("Issue Desc", "Security Description"));
            String maturityHeader = findHeader(headerMap.keySet(), List.of("Maturity Date", "Symbol / Maturity Date"));
            String isinHeader = findHeader(headerMap.keySet(), List.of("ISIN"));

            if (isinHeader == null) {
                log.warn("CSV: ISIN header not present, skipping file.");
                return bonds;
            }
            if (nameHeader == null) nameHeader = isinHeader; // fallback (won't be ideal)
            if (maturityHeader == null) maturityHeader = ""; // may be empty

            for (CSVRecord r : parser) {
                try {
                    String isin = safeGet(r, isinHeader);
                    if (isin.isEmpty()) continue;

                    String name = nameHeader.isEmpty() ? "" : safeGet(r, nameHeader);
                    String mStr = maturityHeader.isEmpty() ? "" : safeGet(r, maturityHeader);

                    bonds.add(new BondRecord(isin, name, parseDate(mStr)));
                } catch (Exception e) {
                    log.warn("Skipping CSV row due to error: {}", e.getMessage());
                }
            }
        }

        log.info("Parsed {} CSV bond records", bonds.size());
        return bonds;
    }

    private String safeGet(CSVRecord r, String header) {
        try {
            return r.isMapped(header) ? r.get(header).trim() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String findHeader(Set<String> headers, List<String> candidates) {
        for (String cand : candidates) {
            for (String h : headers) {
                if (h != null && h.equalsIgnoreCase(cand)) return h;
            }
        }
        return null;
    }

    private LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;

        List<DateTimeFormatter> fmts = List.of(
                DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
                DateTimeFormatter.ISO_LOCAL_DATE
        );

        for (DateTimeFormatter f : fmts) {
            try {
                return LocalDate.parse(s.trim(), f);
            } catch (Exception ignored) {}
        }

        // Try to parse common compact formats like 14FEB2027
        try {
            String su = s.trim().toUpperCase();
            if (su.matches("\\d{1,2}[A-Z]{3}\\d{4}")) {
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("ddMMMyyyy", Locale.ENGLISH);
                return LocalDate.parse(su, fmt);
            }
        } catch (Exception ignored) {}

        return null;
    }

    public record BondRecord(String isin, String name, LocalDate maturityDate) {}
}
