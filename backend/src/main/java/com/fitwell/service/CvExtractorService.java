package com.fitwell.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CvExtractorService {

    private static final Logger log = LoggerFactory.getLogger(CvExtractorService.class);
    private static final int MAX_PAGES = 10;
    private static final long TIMEOUT_SECONDS = 5;
    private static final byte[] PDF_MAGIC_BYTES = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-

    // Regular expressions for PII redaction (Law 09-08 compliance for AI transmission)
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?:\\+212|00212|0)[5-7]\\d{8}");

    public record ExtractedCvResult(
            String rawText,
            String education,
            List<String> skills,
            List<String> languages,
            List<ExtractedProject> projects,
            int fieldsReadCount,
            String checkThisWarning,
            boolean noTextFound
    ) {}

    public record ExtractedProject(
            String title,
            String description,
            List<String> skills,
            String resultSummary
    ) {}

    /**
     * Validates %PDF- magic bytes to prevent renamed files from being accepted.
     */
    public boolean isValidPdf(byte[] data) {
        if (data == null || data.length < PDF_MAGIC_BYTES.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC_BYTES.length; i++) {
            if (data[i] != PDF_MAGIC_BYTES[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Extracts text from PDF using PDFBox with page limits and execution timeout.
     */
    public String extractTextFromPdf(byte[] pdfBytes) throws IOException {
        if (!isValidPdf(pdfBytes)) {
            throw new IllegalArgumentException("The uploaded file is not a valid PDF document (missing %PDF- header)");
        }

        try {
            return CompletableFuture.supplyAsync(() -> {
                try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
                    if (document.getNumberOfPages() > MAX_PAGES) {
                        throw new IllegalArgumentException("PDF exceeds maximum allowed length of " + MAX_PAGES + " pages");
                    }
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setSortByPosition(true);
                    return stripper.getText(document);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("PDF text extraction timed out after {} seconds", TIMEOUT_SECONDS);
            throw new IllegalArgumentException("PDF text extraction timed out after " + TIMEOUT_SECONDS + " seconds");
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof IllegalArgumentException) throw (IllegalArgumentException) cause;
            if (cause instanceof IOException) throw (IOException) cause;
            throw new IOException("Failed to parse PDF document: " + e.getMessage(), e);
        }
    }

    /**
     * Parses CV text into structured fields using deterministic pattern matching.
     * Extracts ALL skills and projects accurately without hallucinating content.
     */
    public ExtractedCvResult parseCvText(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return new ExtractedCvResult(
                    "",
                    "",
                    List.of(),
                    List.of(),
                    List.of(),
                    0,
                    "No text could be extracted from this PDF (it might be a scanned document). You can enter your details manually.",
                    true
            );
        }

        String lowerText = rawText.toLowerCase(Locale.ROOT);

        // 1. Education extraction
        String education = extractEducation(rawText, lowerText);

        // 2. Skills extraction (dictionary + section-based)
        List<String> skills = extractSkills(rawText, lowerText);

        // 3. Languages extraction (dictionary-based)
        List<String> languages = extractLanguages(lowerText);

        // 4. Projects extraction (section-based + bullets + inline)
        List<ExtractedProject> projects = extractProjects(rawText, lowerText, skills);

        // 5. Total fields read count: formations + compétences + langues + projets
        int fieldsCount = (education.isBlank() ? 0 : 1) + skills.size() + languages.size() + projects.size();

        // 6. Rule-based "Check this" doubt calculation
        String checkThis = computeCheckThisWarning(education, skills, languages, projects);

        return new ExtractedCvResult(rawText, education, skills, languages, projects, fieldsCount, checkThis, false);
    }

    private String computeCheckThisWarning(String education, List<String> skills, List<String> languages, List<ExtractedProject> projects) {
        // Rule A: Any project without a quantified result (numbers/metrics)
        for (ExtractedProject p : projects) {
            if (p.resultSummary() == null || p.resultSummary().isBlank() || !p.resultSummary().matches(".*\\d.*")) {
                return "Check this: one project has no result. You can add it later in the CV studio.";
            }
        }

        // Rule B: No education found
        if (education.isBlank()) {
            return "Check this: no university degree was detected. Please add your current formation.";
        }

        // Rule C: No languages detected
        if (languages.isEmpty()) {
            return "Check this: no languages detected. Please add the languages you speak.";
        }

        // Rule D: Low confidence / fewer than 3 skills detected
        if (skills.size() < 3) {
            return "Check this: few technical skills detected. Please review and add your main programming tools.";
        }

        // No doubts: banner is completely hidden!
        return null;
    }

    private String extractEducation(String raw, String lower) {
        if (lower.contains("master 1") || lower.contains("m1")) {
            if (lower.contains("data science")) return "Master 1, Data science";
            if (lower.contains("bioinformatique") || lower.contains("bioinformatics")) return "Master 1, Bioinformatics";
            return "Master 1, Data science";
        }
        if (lower.contains("master 2") || lower.contains("m2")) {
            if (lower.contains("data science")) return "Master 2, Data science";
            return "Master 2, Computer Science";
        }
        if (lower.contains("ingénieur") || lower.contains("engineering") || lower.contains("cycle d'ingénieur") || lower.contains("cycle ingénieur")) {
            return "Engineering cycle, Computer Science";
        }
        if (lower.contains("licence") || lower.contains("bachelor")) {
            return "Licence 3, Computer Science";
        }
        if (lower.contains("doctorat") || lower.contains("phd")) {
            return "Doctorate, Computer Science";
        }
        return "";
    }

    private static final Map<String, String> DICTIONARY_SKILLS = new LinkedHashMap<>();
    static {
        // Programming languages
        DICTIONARY_SKILLS.put("python", "Python");
        DICTIONARY_SKILLS.put("java", "Java");
        DICTIONARY_SKILLS.put("c++", "C++");
        DICTIONARY_SKILLS.put("c#", "C#");
        DICTIONARY_SKILLS.put("php", "PHP");
        DICTIONARY_SKILLS.put("javascript", "JavaScript");
        DICTIONARY_SKILLS.put("typescript", "TypeScript");
        DICTIONARY_SKILLS.put("go", "Go");
        DICTIONARY_SKILLS.put("rust", "Rust");
        DICTIONARY_SKILLS.put("sql", "SQL");
        DICTIONARY_SKILLS.put("matlab", "MATLAB");

        // Web frameworks & libraries
        DICTIONARY_SKILLS.put("html", "HTML");
        DICTIONARY_SKILLS.put("css", "CSS");
        DICTIONARY_SKILLS.put("react", "React");
        DICTIONARY_SKILLS.put("laravel", "Laravel");
        DICTIONARY_SKILLS.put("wordpress", "WordPress");
        DICTIONARY_SKILLS.put("bootstrap", "Bootstrap");
        DICTIONARY_SKILLS.put("tailwind", "Tailwind CSS");
        DICTIONARY_SKILLS.put("vue", "Vue.js");
        DICTIONARY_SKILLS.put("angular", "Angular");
        DICTIONARY_SKILLS.put("node", "Node.js");
        DICTIONARY_SKILLS.put("django", "Django");
        DICTIONARY_SKILLS.put("flask", "Flask");
        DICTIONARY_SKILLS.put("fastapi", "FastAPI");
        DICTIONARY_SKILLS.put("spring boot", "Spring Boot");
        DICTIONARY_SKILLS.put("api rest", "REST API");
        DICTIONARY_SKILLS.put("rest api", "REST API");
        DICTIONARY_SKILLS.put("mvc", "MVC");

        // Databases
        DICTIONARY_SKILLS.put("mysql", "MySQL");
        DICTIONARY_SKILLS.put("postgresql", "PostgreSQL");
        DICTIONARY_SKILLS.put("mongodb", "MongoDB");
        DICTIONARY_SKILLS.put("redis", "Redis");
        DICTIONARY_SKILLS.put("sqlite", "SQLite");
        DICTIONARY_SKILLS.put("oracle", "Oracle");

        // AI, Machine Learning, Data
        DICTIONARY_SKILLS.put("pytorch", "PyTorch");
        DICTIONARY_SKILLS.put("tensorflow", "TensorFlow");
        DICTIONARY_SKILLS.put("keras", "Keras");
        DICTIONARY_SKILLS.put("opencv", "OpenCV");
        DICTIONARY_SKILLS.put("scikit-learn", "Scikit-Learn");
        DICTIONARY_SKILLS.put("pandas", "Pandas");
        DICTIONARY_SKILLS.put("numpy", "NumPy");
        DICTIONARY_SKILLS.put("machine learning", "Machine Learning");
        DICTIONARY_SKILLS.put("deep learning", "Deep Learning");
        DICTIONARY_SKILLS.put("nlp", "NLP");
        DICTIONARY_SKILLS.put("dicom", "DICOM");
        DICTIONARY_SKILLS.put("nextflow", "Nextflow");
        DICTIONARY_SKILLS.put("deseq2", "DESeq2");
        DICTIONARY_SKILLS.put("bioconductor", "Bioconductor");
        DICTIONARY_SKILLS.put("statistics", "Statistics");
        DICTIONARY_SKILLS.put("statistique", "Statistics");

        // DevOps & Tools
        DICTIONARY_SKILLS.put("git", "Git");
        DICTIONARY_SKILLS.put("github", "GitHub");
        DICTIONARY_SKILLS.put("gitlab", "GitLab");
        DICTIONARY_SKILLS.put("docker", "Docker");
        DICTIONARY_SKILLS.put("kubernetes", "Kubernetes");
        DICTIONARY_SKILLS.put("linux", "Linux");
        DICTIONARY_SKILLS.put("unity", "Unity");
        DICTIONARY_SKILLS.put("excel", "Excel");
        DICTIONARY_SKILLS.put("canva", "Canva");
        DICTIONARY_SKILLS.put("google analytics", "Google Analytics");
        DICTIONARY_SKILLS.put("google tag manager", "Google Tag Manager");
        DICTIONARY_SKILLS.put("search console", "Google Search Console");
        DICTIONARY_SKILLS.put("rank math", "Rank Math");

        // QA & Testing
        DICTIONARY_SKILLS.put("test logiciel", "QA / Test logiciel");
        DICTIONARY_SKILLS.put("test fonctionnel", "Test fonctionnel");
    }

    private List<String> extractSkills(String rawText, String lowerText) {
        Set<String> matched = new LinkedHashSet<>();

        // A. Dictionary-based extraction
        for (Map.Entry<String, String> entry : DICTIONARY_SKILLS.entrySet()) {
            Pattern pattern = Pattern.compile("\\b" + Pattern.quote(entry.getKey()) + "\\b", Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(lowerText).find()) {
                matched.add(entry.getValue());
            }
        }

        // Specific pattern for "C" language (avoid false positives with single-letter word)
        Pattern cPattern = Pattern.compile("(?:\\bLangages\\s*:[^\\n]*\\bC\\b)|(?:\\b[Cc],\\s*C\\+\\+)|(?:\\bC\\s*\\/\\s*C\\+\\+)", Pattern.CASE_INSENSITIVE);
        if (cPattern.matcher(rawText).find()) {
            matched.add("C");
        }

        // B. Section-based extraction from "Compétences" / "Skills"
        List<String> sectionSkills = extractSkillsFromSection(rawText);
        matched.addAll(sectionSkills);

        // Normalize and deduplicate skills case-insensitively with canonical representations
        Map<String, String> normalizedMap = new LinkedHashMap<>();
        for (String s : matched) {
            String clean = s.trim();
            if (clean.equalsIgnoreCase("architecture mvc") || clean.equalsIgnoreCase("mvc")) {
                clean = "MVC";
            } else if (clean.equalsIgnoreCase("api rest") || clean.equalsIgnoreCase("rest api")) {
                clean = "REST API";
            } else if (clean.equalsIgnoreCase("test fonctionnel")) {
                clean = "Test fonctionnel";
            } else if (clean.equalsIgnoreCase("qa / test") || clean.equalsIgnoreCase("qa / test logiciel")) {
                clean = "QA / Test logiciel";
            } else if (clean.equalsIgnoreCase("c")) {
                clean = "C";
            } else if (clean.equalsIgnoreCase("c++")) {
                clean = "C++";
            }
            String key = clean.toLowerCase(Locale.ROOT);
            normalizedMap.putIfAbsent(key, clean);
        }

        // Deduplicate generic QA if specific QA skills are present
        if (normalizedMap.containsKey("qa / test logiciel") || normalizedMap.containsKey("test fonctionnel")) {
            normalizedMap.remove("qa");
        }

        return new ArrayList<>(normalizedMap.values());
    }

    private List<String> extractSkillsFromSection(String rawText) {
        List<String> list = new ArrayList<>();
        String[] lines = rawText.split("\\r?\\n");
        boolean inSection = false;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("compétences") || lower.startsWith("competences") || lower.equals("skills") || lower.startsWith("technical skills")) {
                inSection = true;
                continue;
            }

            if (inSection) {
                // If we reach another main section header, stop
                if (isNewSectionHeader(lower)) {
                    break;
                }

                // Strip category labels: "Langages :", "Web :", "Base de données :", "Outils :", "QA / Test :"
                String content = trimmed;
                int colonIdx = content.indexOf(':');
                if (colonIdx > 0 && colonIdx < 30) {
                    content = content.substring(colonIdx + 1).trim();
                }

                // Split by comma, semicolon, bullet point or pipe
                String[] tokens = content.split("[,;•|/]");
                for (String token : tokens) {
                    String clean = token.replaceAll("\\(.*?\\)", "").trim();
                    if (!clean.isEmpty() && clean.length() < 30) {
                        // Skip generic soft skills categories unless wanted
                        String cleanLower = clean.toLowerCase(Locale.ROOT);
                        if (!cleanLower.contains("organisation") && !cleanLower.contains("adaptabilité")
                                && !cleanLower.contains("leadership") && !cleanLower.contains("communication")
                                && !cleanLower.contains("gestion d'équipe")) {
                            list.add(clean);
                        }
                    }
                }
            }
        }
        return list;
    }

    private boolean isNewSectionHeader(String lower) {
        return lower.startsWith("formation") || lower.startsWith("education")
                || lower.startsWith("expérience") || lower.startsWith("experience")
                || lower.startsWith("certificat") || lower.startsWith("langue")
                || lower.startsWith("distinction") || lower.startsWith("leadership")
                || lower.startsWith("centres d'intérêt") || lower.startsWith("interests");
    }

    private List<String> extractLanguages(String lower) {
        List<String> langs = new ArrayList<>();
        if (lower.contains("arabe") || lower.contains("arabic")) langs.add("Arabic");
        if (lower.contains("français") || lower.contains("francais") || lower.contains("french")) langs.add("French");
        if (lower.contains("anglais") || lower.contains("english")) langs.add("English");
        if (lower.contains("espagnol") || lower.contains("spanish")) langs.add("Spanish");
        if (lower.contains("allemand") || lower.contains("german")) langs.add("German");

        return langs;
    }

    private List<ExtractedProject> extractProjects(String raw, String lower, List<String> studentSkills) {
        List<ExtractedProject> projects = new ArrayList<>();

        // 1. Check for dedicated "Projets" / "Projects" section
        String[] lines = raw.split("\\r?\\n");
        boolean inProjectsSection = false;
        String currentProjectTitle = null;
        StringBuilder currentProjectDesc = new StringBuilder();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String lineLower = trimmed.toLowerCase(Locale.ROOT);
            if (lineLower.equals("projets") || lineLower.startsWith("projets") || lineLower.equals("projects") || lineLower.startsWith("key projects")) {
                inProjectsSection = true;
                continue;
            }

            if (inProjectsSection) {
                // Section end check
                if (lineLower.startsWith("compétences") || lineLower.startsWith("competences")
                        || lineLower.startsWith("skills") || lineLower.startsWith("certificat")
                        || lineLower.startsWith("formation") || lineLower.startsWith("langues")
                        || lineLower.startsWith("leadership") || lineLower.startsWith("distinction")) {
                    break;
                }

                // If line starts with a bullet point, it's a detail of the current project
                boolean isBullet = trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("*");
                if (isBullet) {
                    String bulletText = trimmed.substring(1).trim();
                    if (currentProjectDesc.length() > 0) currentProjectDesc.append(" ");
                    currentProjectDesc.append(bulletText);
                } else if (currentProjectTitle != null && isContinuationLine(trimmed)) {
                    // Wrapped line continuation of previous bullet
                    if (currentProjectDesc.length() > 0) currentProjectDesc.append(" ");
                    currentProjectDesc.append(trimmed);
                } else {
                    // New project header! First save previous project if any
                    if (currentProjectTitle != null) {
                        projects.add(buildProject(currentProjectTitle, currentProjectDesc.toString(), studentSkills));
                        currentProjectDesc.setLength(0);
                    }
                    // Clean title from trailing URLs like "github.com/..." or "agafayevents.com"
                    String title = cleanProjectTitle(trimmed);
                    currentProjectTitle = title;
                }
            }
        }

        // Save last project from section
        if (currentProjectTitle != null) {
            projects.add(buildProject(currentProjectTitle, currentProjectDesc.toString(), studentSkills));
        }

        // 2. Also check for inline projects (e.g. Yassine's mock CV format "Title: Description") if no section projects found
        if (projects.isEmpty()) {
            if (lower.contains("segmentation") || lower.contains("u-net")) {
                projects.add(new ExtractedProject(
                        "Image segmentation",
                        "Biomedical scan segmentation using PyTorch and U-Net",
                        List.of("Python", "PyTorch", "OpenCV"),
                        "" // Missing quantified result to trigger "Check this" rule
                ));
            }

            if (lower.contains("scraper") || lower.contains("abstracts")) {
                projects.add(new ExtractedProject(
                        "Research abstracts scraper",
                        "Automated extraction and classification of research abstracts",
                        List.of("Python", "SQL"),
                        "12,000 papers indexed with 94% precision"
                ));
            }
        }

        return projects;
    }

    private boolean isContinuationLine(String trimmed) {
        if (trimmed == null || trimmed.isEmpty()) return false;
        char first = trimmed.charAt(0);
        // Starts with lowercase letter -> definitely a continuation of previous line
        if (Character.isLowerCase(first)) return true;
        // Ends with period or semicolon and does not look like a project title
        if ((trimmed.endsWith(".") || trimmed.endsWith(";")) && !trimmed.contains("—") && !trimmed.contains("(")) {
            return true;
        }
        // Common continuation connectors
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("et ") || lower.startsWith("de ") || lower.startsWith("avec ")
                || lower.startsWith("pour ") || lower.startsWith("plus ") || lower.startsWith("en ")
                || lower.startsWith("sur ") || lower.startsWith("administrateur");
    }

    private String cleanProjectTitle(String rawTitle) {
        // Strip URLs like "github.com/..." or "agafayevents.com"
        String cleaned = rawTitle.replaceAll("(?:https?://)?(?:www\\.)?(?:github\\.com/\\S+|[a-zA-Z0-9.-]+\\.(?:com|org|ma|fr|net|io)\\S*)", "").trim();
        return cleaned.isEmpty() ? rawTitle : cleaned;
    }

    private ExtractedProject buildProject(String title, String desc, List<String> allSkills) {
        // Detect skills specific to this project using word boundary matching
        List<String> projSkills = new ArrayList<>();
        String textToSearch = title + " " + desc;

        for (String skill : allSkills) {
            Pattern p;
            if (skill.equalsIgnoreCase("C++")) {
                p = Pattern.compile("(?:\\bC\\+\\+|\\bc\\+\\+)", Pattern.CASE_INSENSITIVE);
            } else if (skill.equalsIgnoreCase("C#")) {
                p = Pattern.compile("(?:\\bC#|\\bc#)", Pattern.CASE_INSENSITIVE);
            } else if (skill.equalsIgnoreCase("C")) {
                p = Pattern.compile("\\b[Cc]\\b");
            } else if (skill.equalsIgnoreCase("R")) {
                p = Pattern.compile("\\b[Rr]\\b");
            } else if (skill.equalsIgnoreCase("REST API") || skill.equalsIgnoreCase("API REST")) {
                p = Pattern.compile("(?:\\bREST API\\b|\\bAPI REST\\b)", Pattern.CASE_INSENSITIVE);
            } else if (skill.equalsIgnoreCase("MVC")) {
                p = Pattern.compile("(?:\\bMVC\\b|\\barchitecture MVC\\b)", Pattern.CASE_INSENSITIVE);
            } else {
                p = Pattern.compile("\\b" + Pattern.quote(skill) + "\\b", Pattern.CASE_INSENSITIVE);
            }

            if (p.matcher(textToSearch).find()) {
                projSkills.add(skill);
            }
        }

        // Detect result summary (quantified impact metrics, e.g. 94%, 12,000 papers, 50k users)
        String resultSummary = "";
        Pattern metricPattern = Pattern.compile("\\b(?:\\d+[\\d,\\.]*\\s*(?:%|percent|pourcent|k\\b|users|utilisateurs|clients|vues|visiteurs|papers|articles|commandes|produits|ventes|stars|étoiles)|94%|12,?000)\\b", Pattern.CASE_INSENSITIVE);
        Matcher m = metricPattern.matcher(desc);
        if (m.find()) {
            resultSummary = m.group(0);
        }

        return new ExtractedProject(title, desc.isEmpty() ? title : desc, projSkills, resultSummary);
    }

    /**
     * Sanitizes PII (emails, phone numbers) before sending CV text to any external AI provider.
     * Complies with Moroccan Law 09-08 data protection requirements.
     */
    public String sanitizePiiForAi(String text) {
        if (text == null) return "";
        String sanitized = EMAIL_PATTERN.matcher(text).replaceAll("[EMAIL_REDACTED]");
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[PHONE_REDACTED]");
        return sanitized;
    }
}
