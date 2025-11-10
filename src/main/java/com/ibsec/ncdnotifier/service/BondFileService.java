package com.ibsec.ncdnotifier.service;

import org.apache.commons.csv.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipInputStream;

@Service
public class BondFileService {

    private static final Logger log = LoggerFactory.getLogger(BondFileService.class);

    private static final List<String> FILE_URLS = List.of(
            "https://nsearchives.nseindia.com/content/debt/Corporate_bond_report_04-Nov-2025.csv",
            "https://www.nseindia.com/api/reports?archives=%5B%7B%22name%22%3A%22Approved%20list%20of%20GSEC%20and%20TBILL%20(.zip)%22%2C%22type%22%3A%22monthly-reports%22%2C%22category%22%3A%22debt%22%2C%22section%22%3A%22nse-ebp%22%7D%5D&type=nse-ebp&mode=single"
    );

    public List<BondRecord> fetchAllBonds() {
        Set<BondRecord> allBonds = new HashSet<>();

        for (String fileUrl : FILE_URLS) {
            try (InputStream in = openConnectionWithHeaders(fileUrl)) {
                if (in == null) {
                    log.warn("Skipping URL {} as no valid input stream obtained", fileUrl);
                    continue;
                }

                if (fileUrl.endsWith(".zip")) {
                    allBonds.addAll(parseZipFile(in));
                } else {
                    allBonds.addAll(parseCsvStream(in));
                }

            } catch (Exception e) {
                log.error("Error processing file from {}", fileUrl, e);
            }
        }

        log.info("Fetched total {} bonds from all sources", allBonds.size());
        return new ArrayList<>(allBonds);
    }

    /** Opens a connection with browser-like headers and timeouts */
    private InputStream openConnectionWithHeaders(String fileUrl) throws IOException {
        log.info("Attempting to download: {}", fileUrl);

        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Add necessary headers
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        connection.setRequestProperty("Accept", "*/*");
        connection.setRequestProperty("Connection", "keep-alive");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(15000);

        int responseCode = connection.getResponseCode();
        log.info("Response code for {} -> {}", fileUrl, responseCode);

        if (responseCode != 200) {
            log.warn("Failed to fetch file: {} (HTTP {})", fileUrl, responseCode);
            return null;
        }

        return connection.getInputStream();
    }

    private List<BondRecord> parseZipFile(InputStream zipStream) throws IOException {
        List<BondRecord> bonds = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            var entry = zis.getNextEntry();
            while (entry != null) {
                log.info("Processing file inside ZIP: {}", entry.getName());
                bonds.addAll(parseCsvStream(zis));
                entry = zis.getNextEntry();
            }
        }
        return bonds;
    }

    private List<BondRecord> parseCsvStream(InputStream inputStream) throws IOException {
        List<BondRecord> bonds = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            // Skip lines until we reach the header (contains "ISIN")
            String line;
            while ((line = reader.readLine()) != null && !line.contains("ISIN")) {
                // keep skipping
            }

            if (line == null) {
                log.warn("No valid header line found containing 'ISIN'.");
                return bonds;
            }

            // Reconstruct CSV text from header onward
            String csvContent = line + "\n" + reader.lines().reduce("", (a, b) -> a + "\n" + b);

            CSVParser parser = CSVFormat.DEFAULT
                    .withFirstRecordAsHeader()
                    .withTrim()
                    .withIgnoreSurroundingSpaces()
                    .parse(new StringReader(csvContent));

            log.info("Detected CSV headers: {}", parser.getHeaderMap().keySet());

            DateTimeFormatter[] possibleFormats = new DateTimeFormatter[] {
                    DateTimeFormatter.ofPattern("dd-MM-yyyy"),
                    DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd")
            };

            for (CSVRecord record : parser) {
                try {
                    String isin = record.get("ISIN").trim();
                    String name = record.get("Issue Desc").trim();
                    String maturityStr = record.get("Maturity Date").trim();

                    if (isin.isEmpty()) {
                        log.debug("Skipping row with empty ISIN: {}", record);
                        continue;
                    }

                    LocalDate maturity = null;
                    if (!maturityStr.isEmpty()) {
                        for (DateTimeFormatter fmt : possibleFormats) {
                            try {
                                maturity = LocalDate.parse(maturityStr, fmt);
                                break; // stop at first successful format
                            } catch (Exception ignored) {}
                        }
                        if (maturity == null) {
                            log.debug("Unrecognized maturity date format for ISIN {}: {}", isin, maturityStr);
                            continue;
                        }
                    }

                    bonds.add(new BondRecord(isin, name, maturity));

                } catch (IllegalArgumentException e) {
                    log.warn("Header mismatch, skipping row: {}", record);
                } catch (Exception e) {
                    log.warn("Skipping invalid row due to parsing error: {}", record, e);
                }
            }
        }

        log.info("Parsed {} valid bond records", bonds.size());
        return bonds;
    }

    public record BondRecord(String isin, String name, LocalDate maturityDate) {}
}
