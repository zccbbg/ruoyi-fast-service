package com.ruoyi.health;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class HealthFiles {
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\d.])\\d+(?:\\.\\d+)?");
    private static final SmartChineseAnalyzer SEARCH_ANALYZER = new SmartChineseAnalyzer();
    private final Path root;
    private final HealthModels models;
    private final ObjectMapper mapper;

    /** 用途：创建健康文件服务；参数：资料根目录、模型服务和 JSON 解析器；返回值：无。 */
    public HealthFiles(@Value("${health.data-root:}") String dataRoot, HealthModels models, ObjectMapper mapper) {
        this.root = dataRoot.isBlank() ? null : Path.of(dataRoot).toAbsolutePath().normalize();
        this.models = models;
        this.mapper = mapper;
    }

    /** 用途：列出根目录下可用的家人档案；参数：无；返回值：目录名称列表。 */
    public List<String> members() throws IOException {
        if (root == null || !Files.isDirectory(root)) {
            throw new IllegalStateException("请配置存在的 HEALTH_DATA_ROOT 目录");
        }
        try (Stream<Path> children = Files.list(root)) {
            return children.filter(Files::isDirectory).filter(path -> !Files.isSymbolicLink(path))
                .filter(path -> Files.isRegularFile(path.resolve("00_知识库入口.md")))
                .map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    /** 用途：验证并取得成员目录；参数：成员目录名称；返回值：受根目录约束的路径。 */
    public Path member(String name) throws IOException {
        if (name == null || !members().contains(name)) {
            throw new IllegalArgumentException("无效的成员目录");
        }
        return root.resolve(name);
    }

    /** 用途：读取回答引用的成员 Markdown 原文；参数：成员和相对路径；返回值：文件内容。 */
    public String source(String name, String relative) throws IOException {
        Path folder = member(name);
        if (relative == null || !relative.endsWith(".md")) throw new IllegalArgumentException("无效的引用路径");
        Path file = folder.resolve(relative).normalize();
        if (!file.startsWith(folder) || !Files.isRegularFile(file)
            || !file.toRealPath().startsWith(folder.toRealPath())) {
            throw new IllegalArgumentException("引用文件不存在");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    /** 用途：从指定成员的 Markdown 中选取相关片段；参数：成员、本轮问题和历史检索文本；返回值：按相关度排序的引用片段。 */
    public List<Source> search(String name, String question, String history) throws IOException {
        Path folder = member(name);
        List<Source> found = new ArrayList<>();
        try (Stream<Path> files = Files.walk(folder, 2)) {
            for (Path file : files.filter(path -> Files.isRegularFile(path) && !Files.isSymbolicLink(path))
                .filter(path -> path.toString().endsWith(".md"))
                .filter(path -> !path.toString().contains("待确认")).toList()) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                for (String section : content.split("(?m)(?=^## )")) {
                    if (section.isBlank()) continue;
                    String text = section.length() > 2200 ? section.substring(0, 2200) : section;
                    String relative = folder.relativize(file).toString().replace('\\', '/');
                    Matcher date = DATE.matcher(file.getFileName().toString());
                    found.add(new Source(relative, date.find() ? date.group() : "日期见原文", text, 0));
                }
            }
        }
        Set<String> currentTerms = searchTerms(question);
        Set<String> historyTerms = searchTerms(history);
        Set<String> allTerms = new HashSet<>(currentTerms);
        allTerms.addAll(historyTerms);
        Map<String, Integer> frequency = new HashMap<>();
        List<Set<String>> sourceTerms = new ArrayList<>();
        for (Source source : found) {
            Set<String> terms = searchTerms(source.text() + " " + source.path());
            sourceTerms.add(terms);
            for (String term : allTerms) {
                if (terms.contains(term)) frequency.merge(term, 1, Integer::sum);
            }
        }
        List<Source> scored = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            Source source = found.get(i);
            int score = score(currentTerms, historyTerms, frequency, found.size(), sourceTerms.get(i));
            if (score > 0) scored.add(new Source(source.path(), source.date(), source.text(), score));
        }
        return scored.stream().sorted(Comparator.comparingInt(Source::score).reversed()).limit(8).toList();
    }

    /** 用途：使用中文分词器提取去重后的检索词；参数：检索文本；返回值：词项集合。 */
    private Set<String> searchTerms(String text) throws IOException {
        Set<String> terms = new HashSet<>();
        if (text == null || text.isBlank()) return terms;
        try (TokenStream stream = SEARCH_ANALYZER.tokenStream("content", text)) {
            CharTermAttribute word = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while (stream.incrementToken()) terms.add(word.toString());
            stream.end();
        }
        return terms;
    }

    /** 用途：按分词匹配度和档案中的稀有程度计算相关性，并提高本轮问题权重；参数：本轮词、历史词、词频、片段数和资料词项；返回值：相关分数。 */
    private int score(Set<String> currentTerms, Set<String> historyTerms, Map<String, Integer> frequency,
                      int sourceCount, Set<String> sourceTerms) {
        int result = 0;
        Set<String> terms = new HashSet<>(historyTerms);
        terms.addAll(currentTerms);
        for (String term : terms) {
            if (!sourceTerms.contains(term)) continue;
            int rarity = 1 + 8 * (sourceCount - frequency.getOrDefault(term, sourceCount)) / sourceCount;
            result += rarity * (currentTerms.contains(term) ? 3 : 1);
        }
        return result;
    }

    /** 用途：读取并初始化成员指标趋势；参数：成员；返回值：按日期排列的结构化指标。 */
    public List<Observation> trends(String name) throws IOException {
        Path folder = member(name);
        Path csv = folder.resolve("指标数据.csv");
        if (!Files.exists(csv)) seedTrends(folder, csv);
        List<Observation> result = new ArrayList<>();
        for (String line : Files.readAllLines(csv, StandardCharsets.UTF_8).stream().skip(1).toList()) {
            String[] parts = line.split(",", -1);
            if (parts.length == 6) {
                result.add(new Observation(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]));
            }
        }
        return result.stream().sorted(Comparator.comparing(Observation::date)).toList();
    }

    /** 用途：从现有趋势摘要的数值表初始化指标文件；参数：成员目录和目标文件；返回值：无。 */
    private void seedTrends(Path folder, Path csv) throws IOException {
        Path legacy = folder.resolve("03_指标趋势.md");
        List<String> rows = new ArrayList<>(List.of("日期,指标,数值,单位,来源,状态"));
        if (Files.exists(legacy)) {
            String[] lines = Files.readString(legacy, StandardCharsets.UTF_8).split("\\R");
            String[] header = new String[0];
            for (String line : lines) {
                if (line.startsWith("## ")) {
                    header = new String[0];
                    continue;
                }
                if (!line.startsWith("|") || !line.endsWith("|")) continue;
                String[] cells = line.substring(1, line.length() - 1).split("\\|", -1);
                for (int i = 0; i < cells.length; i++) cells[i] = cells[i].trim();
                if (cells.length < 3) continue;
                if (line.contains("---")) continue;
                if ("日期".equals(cells[0]) || "检查日期".equals(cells[0]) || "指标".equals(cells[0])) {
                    header = cells;
                    continue;
                }
                if (header.length == 0) continue;
                if (DATE.matcher(cells[0]).matches()) {
                    for (int i = 1; i < Math.min(header.length, cells.length); i++) {
                        addTrendRow(rows, cells[0], header[i], cells[i]);
                    }
                } else if ("指标".equals(header[0])) {
                    for (int i = 1; i < Math.min(header.length, cells.length); i++) {
                        if (DATE.matcher(header[i]).matches()) addTrendRow(rows, header[i], cells[0], cells[i]);
                    }
                }
            }
        }
        Files.write(csv, rows, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    /** 用途：仅将明确的单个数值写成趋势行；参数：结果行、日期、指标名和原始单元格；返回值：无。 */
    private void addTrendRow(List<String> rows, String date, String name, String raw) {
        String value = raw.trim();
        Matcher number = NUMBER.matcher(value);
        if (!number.lookingAt() || value.contains("、") || value.contains("×")) return;
        String unit = value.substring(number.end()).trim();
        if (unit.startsWith("/") || unit.startsWith("<") || unit.startsWith(">")) return;
        String row = date + "," + clean(name) + "," + number.group() + "," + clean(unit)
            + ",03_指标趋势.md,已整理";
        if (!rows.contains(row)) rows.add(row);
    }

    /** 用途：过滤 CSV 单元格中的分隔字符；参数：原始文本；返回值：安全文本。 */
    private String clean(String value) {
        return value == null ? "" : value.trim().replace(',', '，').replace('\n', ' ').replace('\r', ' ');
    }

    /** 用途：上传报告原件并生成待确认草稿；参数：成员、报告日期、标题和文件；返回值：识别草稿。 */
    public Draft upload(String name, String date, String title, MultipartFile file) throws IOException {
        Path folder = member(name);
        LocalDate.parse(date);
        if (title == null || title.isBlank() || file.isEmpty() || file.getSize() > 15_000_000) {
            throw new IllegalArgumentException("报告标题和文件不能为空，文件不得超过 15 MB");
        }
        byte[] bytes = file.getBytes();
        String extension = type(bytes);
        String id = UUID.randomUUID().toString();
        Path originals = Files.createDirectories(folder.resolve("原始报告"));
        if (!originals.toRealPath().startsWith(folder.toRealPath())) {
            throw new IllegalStateException("原始报告目录超出健康资料根目录");
        }
        Path original = originals.resolve(date + "_" + id + extension);
        Files.write(original, bytes, StandardOpenOption.CREATE_NEW);
        try {
            String transcription;
            if (".pdf".equals(extension)) {
                transcription = pdfText(bytes);
                if (transcription.length() < 40) transcription = pdfVision(bytes);
            } else {
                transcription = imageVision(bytes, extension);
            }
            String prompt = "从下面的报告原文提取 JSON 对象，字段为 summary（文字摘要）、observations（数组，每项包含 date,name,value,unit）、todos（文字数组）。"
                + "只抄报告明确记载的事实；缺失字段留空，不推测诊断。原文：\n" + transcription;
            String json = models.ask(models.select(null, "REPORT"), prompt);
            JsonNode node = parseJson(json);
            List<Observation> observations = new ArrayList<>();
            for (JsonNode item : node.path("observations")) {
                String value = item.path("value").asText();
                if (value.matches("-?\\d+(\\.\\d+)?")) {
                    observations.add(new Observation(date, item.path("name").asText(), value,
                        item.path("unit").asText(), "报告转写/" + date + "_" + id + ".md", "已核对"));
                }
            }
            List<String> todos = new ArrayList<>();
            node.path("todos").forEach(item -> todos.add(item.asText()));
            Draft draft = new Draft(id, name, date, title.trim(), original.getFileName().toString(),
                transcription, node.path("summary").asText(), observations, todos);
            Path pending = Files.createDirectories(folder.resolve("待确认"));
            if (!pending.toRealPath().startsWith(folder.toRealPath())) {
                throw new IllegalStateException("草稿目录超出健康资料根目录");
            }
            mapper.writeValue(pending.resolve(id + ".json").toFile(), draft);
            return draft;
        } catch (IOException | RuntimeException ex) {
            Files.deleteIfExists(original);
            Files.deleteIfExists(folder.resolve("待确认").resolve(id + ".json"));
            throw ex;
        }
    }

    /** 用途：校验上传文件的真实格式；参数：文件字节；返回值：允许的扩展名。 */
    private String type(byte[] bytes) {
        if (bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F') return ".pdf";
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216) return ".jpg";
        if (bytes.length >= 8 && (bytes[0] & 255) == 137 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') return ".png";
        throw new IllegalArgumentException("仅支持 PDF、JPG、PNG 报告");
    }

    /** 用途：读取可复制 PDF 的文本；参数：PDF 字节；返回值：报告文字。 */
    private String pdfText(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            if (document.getNumberOfPages() > 10) throw new IllegalArgumentException("目前最多支持 10 页 PDF");
            stripper.setEndPage(document.getNumberOfPages());
            return stripper.getText(document).trim();
        }
    }

    /** 用途：识别扫描 PDF 前三页；参数：PDF 字节；返回值：转写文字。 */
    private String pdfVision(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > 3) throw new IllegalArgumentException("扫描 PDF 目前最多支持 3 页");
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < Math.min(document.getNumberOfPages(), 3); i++) {
                BufferedImage page = renderer.renderImageWithDPI(i, 130);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(page, "png", output);
                result.append(imageVision(output.toByteArray(), ".png")).append('\n');
            }
            return result.toString();
        }
    }

    /** 用途：识别报告图片的原始文字；参数：图片字节与扩展名；返回值：转写文字。 */
    private String imageVision(byte[] bytes, String extension) {
        HealthModels.ModelConfig model = models.select(null, "REPORT");
        if ("DEEPSEEK".equals(model.provider())) {
            throw new IllegalArgumentException("图片及扫描 PDF 需要配置支持视觉识别的报告模型");
        }
        return models.readImage(model, bytes,
            ".png".equals(extension) ? MimeTypeUtils.IMAGE_PNG : MimeTypeUtils.IMAGE_JPEG,
            "逐字转写这张健康报告图片。看不清的地方写[不清]，不要补写或下诊断。");
    }

    /** 用途：解析模型返回的 JSON 草稿；参数：模型输出；返回值：JSON 节点。 */
    private JsonNode parseJson(String output) throws IOException {
        int start = output.indexOf('{');
        int end = output.lastIndexOf('}');
        if (start < 0 || end < start) throw new IOException("模型没有返回可核对的报告草稿");
        return mapper.readTree(output.substring(start, end + 1));
    }

    /** 用途：列出某位成员仍待核对的报告；参数：成员；返回值：报告草稿列表。 */
    public List<Draft> drafts(String name) throws IOException {
        Path folder = member(name).resolve("待确认");
        if (!Files.isDirectory(folder)) return List.of();
        List<Draft> result = new ArrayList<>();
        try (Stream<Path> paths = Files.list(folder)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                result.add(mapper.readValue(path.toFile(), Draft.class));
            }
        }
        return result;
    }

    /** 用途：读取待核对报告的原件；参数：成员和草稿编号；返回值：原件字节。 */
    public byte[] original(String name, String id) throws IOException {
        Path folder = member(name);
        if (id == null || !id.matches("[a-f0-9-]{36}")) throw new IllegalArgumentException("无效的草稿编号");
        Path pending = folder.resolve("待确认").resolve(id + ".json");
        if (!Files.isRegularFile(pending)) throw new IllegalArgumentException("待确认报告不存在");
        Draft draft = mapper.readValue(pending.toFile(), Draft.class);
        Path file = folder.resolve("原始报告").resolve(draft.originalName()).normalize();
        if (!file.startsWith(folder) || !Files.isRegularFile(file)
            || !file.toRealPath().startsWith(folder.toRealPath())) {
            throw new IllegalArgumentException("原始报告不存在");
        }
        return Files.readAllBytes(file);
    }

    /** 用途：保存用户核对后的报告并更新资料；参数：成员与已编辑草稿；返回值：无。 */
    public synchronized void confirm(String name, Draft edited) throws IOException {
        Path folder = member(name);
        if (edited == null || edited.id() == null || !name.equals(edited.member())
            || !edited.id().matches("[a-f0-9-]{36}")) {
            throw new IllegalArgumentException("无效的报告草稿");
        }
        Path pending = folder.resolve("待确认").resolve(edited.id() + ".json");
        if (!Files.isRegularFile(pending)) throw new IllegalArgumentException("草稿不存在或已经确认");
        Draft saved = mapper.readValue(pending.toFile(), Draft.class);
        if (saved.originalName() == null) throw new IllegalArgumentException("草稿缺少报告原件");
        Path originalFile = folder.resolve("原始报告").resolve(saved.originalName()).normalize();
        if (!originalFile.startsWith(folder) || !Files.isRegularFile(originalFile)
            || !originalFile.toRealPath().startsWith(folder.toRealPath())) {
            throw new IllegalArgumentException("报告原件不存在");
        }
        if (edited.date() == null || edited.title() == null || edited.title().isBlank()
            || edited.transcription() == null || edited.summary() == null
            || edited.title().length() > 80 || edited.summary().length() > 10000
            || edited.transcription().length() > 100000
            || (edited.todos() != null && (edited.todos().size() > 100 || edited.todos().stream().anyMatch(item -> item == null)))) {
            throw new IllegalArgumentException("请核对报告日期、名称和内容长度");
        }
        LocalDate.parse(edited.date());
        String markdownName = edited.date() + "_" + edited.id() + ".md";
        Path transcripts = Files.createDirectories(folder.resolve("报告转写"));
        if (!transcripts.toRealPath().startsWith(folder.toRealPath())) {
            throw new IllegalStateException("转写目录超出健康资料根目录");
        }
        Path transcript = transcripts.resolve(markdownName);
        String marker = "<!-- health-report:" + edited.id() + " -->";
        String summary = edited.summary() == null ? "" : edited.summary().trim();
        String report = "# " + clean(edited.title()) + "\n\n报告日期：" + edited.date() + "\n原始文件：原始报告/"
            + saved.originalName() + "\n信息性质：报告转写，人工核对\n\n## 关键记录\n" + summary
            + "\n\n## 报告原文转写\n" + edited.transcription() + "\n";
        if (!Files.exists(transcript)) Files.writeString(transcript, report, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        String link = "[" + edited.date() + " " + clean(edited.title()) + "](报告转写/" + markdownName + ")";
        appendOnce(folder.resolve("00_知识库入口.md"), marker, "\n- " + link + "\n");
        appendOnce(folder.resolve("01_健康档案.md"), marker, "\n## " + edited.date() + " " + clean(edited.title()) + "\n" + summary + "\n");
        appendOnce(folder.resolve("02_检查时间线.md"), marker, "\n- " + edited.date() + " " + link + "：" + summary + "\n");
        appendOnce(folder.resolve("03_指标趋势.md"), marker, "\n## " + edited.date() + " " + clean(edited.title()) + "\n" + summary + "\n");
        if (edited.todos() != null && !edited.todos().isEmpty()) {
            appendOnce(folder.resolve("04_待办与复查.md"), marker,
                "\n## " + edited.date() + " 待确认事项\n- " + String.join("\n- ", edited.todos()) + "\n");
        }
        Path csv = folder.resolve("指标数据.csv");
        if (!Files.exists(csv)) seedTrends(folder, csv);
        String old = Files.readString(csv, StandardCharsets.UTF_8);
        if (edited.observations() != null) {
            for (Observation observation : edited.observations()) {
                if (observation == null || observation.name() == null || observation.name().isBlank()
                    || observation.value() == null || !observation.value().matches("-?\\d+(\\.\\d+)?")) continue;
                String row = edited.date() + "," + clean(observation.name()) + "," + observation.value()
                    + "," + clean(observation.unit()) + ",报告转写/" + markdownName + ",已核对";
                if (old.lines().noneMatch(row::equals)) {
                    Files.writeString(csv, row + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
                    old += row + "\n";
                }
            }
        }
        Files.delete(pending);
    }

    /** 用途：在指定 Markdown 中按报告编号防止重复追加；参数：路径、标记、正文；返回值：无。 */
    private void appendOnce(Path file, String marker, String content) throws IOException {
        if (!Files.exists(file)) return;
        if (!Files.readString(file, StandardCharsets.UTF_8).contains(marker)) {
            Files.writeString(file, "\n" + marker + content, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
    }

    public record Source(String path, String date, String text, int score) {}
    public record Observation(String date, String name, String value, String unit, String source, String status) {}
    public record Draft(String id, String member, String date, String title, String originalName,
                        String transcription, String summary, List<Observation> observations, List<String> todos) {}
}
