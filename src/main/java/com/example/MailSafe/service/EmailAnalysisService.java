package com.example.MailSafe.service;

import com.example.MailSafe.MailSafeApplication;
import com.example.MailSafe.models.Attachment;
import com.example.MailSafe.models.MailTask;
import com.example.MailSafe.utils.SpfChecker;
import io.github.cdimascio.dotenv.Dotenv;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.Name;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;



@Service
public class EmailAnalysisService {
    Dotenv de = Dotenv.load();
    public int analyzeEmail(MailTask task, boolean useAI) {
        int score = 0;
        String rawEmail = task.getRawEmail();
        if (rawEmail==null){return -1;}
        Document doc = Jsoup.parse(rawEmail);
        Elements links = doc.select("a[href]");
        List<String> urls = new ArrayList<>();
        links.forEach(el -> urls.add(el.attr("href")));

        for (String url : urls) {
            if (isSuspiciousUrl(url)) {
                score += 1;
            }
        }

        // 2. 分析附件
        if (task.getAttachments() != null) {
            for (Attachment att : task.getAttachments()) {
                if (isMaliciousFile(att.getData())) {
                    score += 2;
                }
            }
        }

        // 3.分析来源
        if (isUnreliableSource(task.getSourceAddr(), task.getSourceIp())) {
            score += 1;
        }

        if(useAI){
            score += AIAnalysis(task.getRawEmail());
        }

        if (containsJs(rawEmail)){
            score += 1;
        }

        // 返回等级
        return score;
    }

    private boolean isSuspiciousUrl(String url) {
        //get API key from .env
        Dotenv dev = Dotenv.load();

        String apiKey = dev.get("GOOG_SAFE_BROWSING_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            MailSafeApplication.logger.warn("No GSB API key found.");
            return false;
        }
        String apiUrl = "https://safebrowsing.googleapis.com/v4/threatMatches:find?key=" + apiKey;
        try{
            String jsonRequest = "{"
                    + "\"client\": {"
                    + "   \"clientId\": \"MailSafeApp\","
                    + "   \"clientVersion\": \"1.0\""
                    + "},"
                    + "\"threatInfo\": {"
                    + "   \"threatTypes\": [\"MALWARE\", \"SOCIAL_ENGINEERING\", \"UNWANTED_SOFTWARE\", \"POTENTIALLY_HARMFUL_APPLICATION\"],"
                    + "   \"platformTypes\": [\"ANY_PLATFORM\"],"
                    + "   \"threatEntryTypes\": [\"URL\"],"
                    + "   \"threatEntries\": [ {\"url\": \"" + url + "\"} ]"
                    + "}}";

            // 发送 HTTP POST 请求
            java.net.URL obj = new java.net.URL(apiUrl);
            java.net.HttpURLConnection con = (java.net.HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            con.setDoOutput(true);

            try (java.io.OutputStream os = con.getOutputStream()) {
                byte[] input = jsonRequest.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 读取响应
            int responseCode = con.getResponseCode();
            if (responseCode == 200) {
                try (java.io.InputStream is = con.getInputStream();
                     java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A")) {
                    String response = s.hasNext() ? s.next() : "";

                    // 如果响应包含 "matches"，说明有风险
                    return response.contains("\"matches\"");
                }
            } else {
                MailSafeApplication.logger.error("Safe Browsing API request failed, code=" + responseCode);
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean isMaliciousFile(byte[] fileData) {
        String host = de.get("CLAMAV_HOST");
        if (host == null || host.isEmpty()) {
            host = "localhost";
        }
        int port;
        try {
            port = Integer.parseInt(de.get("CLAMAV_PORT"));
        } catch (NumberFormatException | NullPointerException e) {
            port = 3310;
        }

        // INSTREAM 协议：先发 "zINSTREAM\0"，然后分块发送：4字节大端长度 + 数据块；最后发 0 长度结束
        final int CHUNK = 8192;

        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(8000);

            try (java.io.OutputStream out = socket.getOutputStream();
                 java.io.InputStream in = socket.getInputStream()) {

                // 1) 命令
                out.write("zINSTREAM\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));

                // 2) 流式分块发送
                int offset = 0;
                while (offset < fileData.length) {
                    int len = Math.min(CHUNK, fileData.length - offset);
                    // 写入4字节长度（大端）
                    out.write(new byte[] {
                            (byte) ((len >>> 24) & 0xFF),
                            (byte) ((len >>> 16) & 0xFF),
                            (byte) ((len >>> 8) & 0xFF),
                            (byte) (len & 0xFF)
                    });
                    out.write(fileData, offset, len);
                    offset += len;
                }

                // 3) 发送结束块（长度为0）
                out.write(new byte[] {0, 0, 0, 0});
                out.flush();

                // 4) 读取 clamd 响应，例如：
                // "stream: OK" 或 "stream: Eicar-Test-Signature FOUND"
                String resp = readClamdLine(in);
                if (resp == null) return false;

                // 认为包含 "FOUND" 即为恶意
                // 你也可以把签名名解析出来记录到 evidence 里
                return resp.contains("FOUND");
            }
        } catch (Exception e) {
            // 扫描失败时不要中断主流程，可视为“未知”，这里返回 false 并记录日志
            // 你可以接入项目的 Logger
            MailSafeApplication.logger.error("ClamAV scan error: " + e.getMessage());
            return false;
        }
    }

    private String readClamdLine(java.io.InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream(128);
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
        }
        return buf.size() == 0 && b == -1 ? null : buf.toString(java.nio.charset.StandardCharsets.US_ASCII);
    }


    private int AIAnalysis(String rawEmail){
        // 模型与 endpoint 可用环境变量覆盖
        final String endpoint = de.get("OLLAMA_ENDPOINT", "http://localhost:11434/api/chat");
        final String model = de.get("OLLAMA_MODEL", "qwen2.5:7b-instruct");

        // 限制传给模型的邮件长度，避免超时或费用（这里只截 4KB）
        final String truncated = truncateUtf8(rawEmail, 4096);

        // system prompt 强制模型只返回 JSON 结构，便于解析
        final String systemPrompt = "你是邮件安全分析员。判断给定邮件是否为钓鱼/包含恶意代码。" +
                "严格**只**返回 JSON，不要输出任何文本或解释，不要包含任何非英语（美国）字符，格式如下：" +
                "{\"risk\":0|1|2,\"reasons\":[\"...\",\"...\"]}。 " +
                "risk 含义：0=安全，1=可疑，2=高风险。";

        final String userPrompt = "邮件正文:\n" + truncated;

        try {
            ObjectMapper om = new ObjectMapper();

            Map<String,Object> req = new LinkedHashMap<>();
            req.put("model", model);
            req.put("stream", false);

            Map<String,Object> options = new HashMap<>();
            options.put("temperature", 0.1);
            req.put("options", options);

            List<Map<String,String>> messages = new ArrayList<>();
            messages.add(Map.of("role","system","content", systemPrompt));
            messages.add(Map.of("role","user","content", userPrompt));
            req.put("messages", messages);

            String jsonReq = om.writeValueAsString(req);

            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonReq))
                    .build();

            HttpClient client = null;
            try {
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
            } catch (Exception e) {
                MailSafeApplication.logger.error("LLM API error: " + e.getMessage());
            }

            HttpResponse<String> resp = client.send(httpReq, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                System.err.println("LLM API error code=" + resp.statusCode() + " body=" + resp.body());
                return 1; // 保守：可疑
            }

            String content = null;
            try {
                JsonNode root = om.readTree(resp.body());
                // 优先取 message.content
                if (root.has("message") && root.path("message").has("content")) {
                    content = root.path("message").path("content").asText();
                } else if (root.has("choices") && root.path("choices").isArray() && root.path("choices").size() > 0) {
                    // 兼容 OpenAI 风格
                    JsonNode first = root.path("choices").get(0);
                    if (first.has("message") && first.path("message").has("content")) {
                        content = first.path("message").path("content").asText();
                    } else if (first.has("text")) {
                        content = first.path("text").asText();
                    }
                } else {
                    // 兜底把整个 body 当作 content
                    content = resp.body();
                }
            } catch (Exception e) {
                // 解析 response JSON 失败，尝试把 body 当作 content
                content = resp.body();
            }

            if (content == null) content = "";

            // content 应该是 JSON，尝试解析出 risk 字段
            try {
                JsonNode result = om.readTree(content);
                int risk = result.path("risk").asInt(Integer.MIN_VALUE);
                if (risk == Integer.MIN_VALUE) {
                    // 未找到 risk 字段，尝试用正则抽取 "risk":n
                    int heur = tryExtractRiskHeuristically(content);
                    return heur < 0 ? 1 : heur;
                }
                if (risk < 0) return 1;
                if (risk > 2) risk = 2;
                return risk;
            } catch (Exception e) {
                // 模型没有严格返回 JSON，尝试从文本中抽取
                int heur = tryExtractRiskHeuristically(content);
                return heur < 0 ? 1 : heur;
            }
        } catch (Exception e) {
            e.printStackTrace();
            // 出错时返回保守值
            return 1;
        }
    }

    // 辅助：截断字符串（按字符数）
    private static String truncateUtf8(String s, int maxChars) {
        if (s == null) return "";
        if (s.length() <= maxChars) return s;
        return s.substring(0, maxChars) + "\n...[truncated]";
    }

    // 辅助：从任意文本中尝试抽取 risk 值
    private static int tryExtractRiskHeuristically(String text) {
        if (text == null) return -1;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"risk\"\\s*:\\s*(\\d)").matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        // 额外关键字规则（兜底）：优先匹配高风险/可疑关键词
        String lower = text.toLowerCase();
        if (lower.contains("phishing") || lower.contains("malware") || lower.contains("found") || lower.contains("credential")) return 2;
        if (lower.contains("suspicious") || lower.contains("possibly") || lower.contains("maybe")) return 1;
        return -1;
    }

    private boolean containsJs(String rawEmail){
        if (rawEmail == null || rawEmail.isEmpty()) return false;

        // 1) 正则检测直接 <script> 标签
        Pattern scriptTag = Pattern.compile("(?i)<\\s*script[^>]*>", Pattern.CASE_INSENSITIVE);
        if (scriptTag.matcher(rawEmail).find()) {
            return true;
        }

        // 2) 正则检测常见的危险协议
        Pattern dangerousHref = Pattern.compile("(?i)href\\s*=\\s*['\"]?\\s*(javascript:|vbscript:|data:)", Pattern.CASE_INSENSITIVE);
        if (dangerousHref.matcher(rawEmail).find()) {
            return true;
        }

        // 3) 用 Jsoup 解析 HTML，检测内联事件属性 (onerror, onclick, onload, onmouseover...)
        Document doc = Jsoup.parse(rawEmail);
        for (Element el : doc.getAllElements()) {
            for (org.jsoup.nodes.Attribute attr : el.attributes()) {
                String attrName = attr.getKey().toLowerCase();
                if (attrName.startsWith("onload")|| attrName.startsWith("onmouseover")||attrName.startsWith("onerror")) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isUnreliableSource(String sourceAddr, String sourceIp){
        return !SpfChecker.isIpAllowed(sourceAddr, sourceIp) || isIpInSpamhaus(sourceIp);
    }

    private boolean isIpInSpamhaus(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length != 4) return false;

            String query = parts[3] + "." + parts[2] + "." + parts[1] + "." + parts[0] + ".zen.spamhaus.org";

            Lookup lookup = new Lookup(Name.fromString(query));
            Record[] records = lookup.run();
            if (records != null && records.length > 0) {
                // 命中黑名单
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
