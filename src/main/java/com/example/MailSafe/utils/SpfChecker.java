package com.example.MailSafe.utils;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;
import java.net.InetAddress;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpfChecker {

    // 递归 include 的最大深度，避免循环
    private static final int MAX_DEPTH = 10;

    /**
     * 校验来源 IP 是否被 mailFrom（邮箱地址或域名）的 SPF 授权。
     * 返回 true 表示 SPF "Pass"（授权）；false 表示非授权（或解析失败时不授权）。
     */
    public static boolean isIpAllowed(String mailFrom, String sourceIp) {
        String domain = extractDomain(mailFrom);
        if (domain == null) return false;
        try {
            InetAddress ip = InetAddress.getByName(sourceIp);
            return evalDomainSpf(domain, ip, 0);
        } catch (Exception e) {
            return false;
        }
    }

    // 提取邮箱的域名部分；如果传入的本身是域名，也返回它
    private static String extractDomain(String addrOrDomain) {
        if (addrOrDomain == null || addrOrDomain.isBlank()) return null;
        String s = addrOrDomain.trim();
        int at = s.lastIndexOf('@');
        if (at >= 0 && at < s.length() - 1) return s.substring(at + 1).toLowerCase(Locale.ROOT);
        // 简单判断是否像域名
        if (s.contains(".")) return s.toLowerCase(Locale.ROOT);
        return null;
    }

    private static boolean evalDomainSpf(String domain, InetAddress ip, int depth) throws TextParseException {
        if (depth > MAX_DEPTH) return false;

        String spf = fetchSpfRecord(domain);
        if (spf == null) return false;

        // 去掉前缀 v=spf1
        String body = spf.trim();
        if (body.toLowerCase(Locale.ROOT).startsWith("v=spf1")) {
            body = body.substring(6).trim();
        }

        // 逐个机制匹配，遇到明确的 Pass/Fail 就返回
        String[] toks = body.split("\\s+");
        for (String tok : toks) {
            if (tok.isBlank()) continue;

            // 解析限定符
            char qual = '+';
            char c0 = tok.charAt(0);
            if (c0 == '+' || c0 == '-' || c0 == '~' || c0 == '?') {
                qual = c0;
                tok = tok.substring(1);
            }

            // 机制与参数
            String mech;
            String arg = null;
            int colon = tok.indexOf(':');
            if (colon >= 0) {
                mech = tok.substring(0, colon).toLowerCase(Locale.ROOT);
                arg = tok.substring(colon + 1);
            } else {
                // 可能还有 /cidr（针对 a/mx）
                mech = tok.toLowerCase(Locale.ROOT);
            }

            Boolean match = null; // null = 不匹配或未知；true=匹配
            switch (mech) {
                case "ip4":
                case "ip6":
                    if (arg != null) match = cidrMatch(ip, arg);
                    break;
                case "include":
                    if (arg != null) {
                        match = evalDomainSpf(arg, ip, depth + 1);
                    }
                    break;
                case "a":
                    match = matchA(ip, arg != null ? arg : domain);
                    break;
                case "mx":
                    match = matchMX(ip, arg != null ? arg : domain);
                    break;
                case "all":
                    match = true; // “all”总是匹配
                    break;
                default:
                    // 未实现机制（exists, ptr 等）——跳过
                    break;
            }

            if (match != null && match) {
                // 依据限定符返回
                // +（或无前缀）=Pass；- = Fail；~ = SoftFail；? = Neutral
                if (qual == '-' ) return false; // 明确失败
                if (qual == '+' ) return true;  // 授权
                if (qual == '~' ) return false; // SoftFail：可按需区分，这里视为不授权
                if (qual == '?' ) return false; // Neutral：视为不授权
            }
        }

        // 未命中任何机制，按未授权处理
        return false;
    }

    /** 查询域的 SPF 记录（TXT 中以 v=spf1 开头的记录）。可能存在多条 TXT，挑出 SPF 那条并拼接。 */
    private static String fetchSpfRecord(String domain) throws TextParseException {
        Lookup lookup = new Lookup(domain, Type.TXT);
        Record[] records = lookup.run();
        if (records == null) return null;
        for (Record r : records) {
            if (r instanceof TXTRecord txt) {
                @SuppressWarnings("unchecked")
                List<String> strings = txt.getStrings();
                String joined = String.join("", strings); // SPF 可能被拆分成多段
                if (joined.toLowerCase(Locale.ROOT).startsWith("v=spf1")) {
                    return joined;
                }
            }
        }
        return null;
    }

    /** CIDR 匹配：arg 形如 1.2.3.0/24 或 2001:db8::/32 */
    private static boolean cidrMatch(InetAddress ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String base = parts[0];
            int prefix = parts.length > 1 ? Integer.parseInt(parts[1]) : (ip.getAddress().length == 4 ? 32 : 128);
            InetAddress net = InetAddress.getByName(base);
            byte[] a = ip.getAddress();
            byte[] b = net.getAddress();
            if (a.length != b.length) return false; // IPv4/IPv6 不同族
            int full = prefix / 8;
            int rem = prefix % 8;
            for (int i = 0; i < full; i++) {
                if (a[i] != b[i]) return false;
            }
            if (rem == 0) return true;
            int mask = -(1 << (8 - rem)) & 0xFF;
            return (a[full] & mask) == (b[full] & mask);
        } catch (Exception e) {
            return false;
        }
    }

    /** a[:domain][/cidr] 机制：解析 A/AAAA，并可选 CIDR。 */
    private static boolean matchA(InetAddress ip, String arg) throws TextParseException {
        String domain = arg;
        Integer cidr = null;

        Matcher m = Pattern.compile("^([^/]+)(?:/(\\d+))?$").matcher(arg);
        if (m.matches()) {
            domain = m.group(1);
            if (m.group(2) != null) cidr = Integer.parseInt(m.group(2));
        }

        List<InetAddress> addrs = resolveA_AAAA(domain);
        for (InetAddress a : addrs) {
            String base = a.getHostAddress() + "/" + (cidr != null ? cidr : (a.getAddress().length == 4 ? 32 : 128));
            if (cidrMatch(ip, base)) return true;
        }
        return false;
    }

    /** mx[:domain][/cidr] 机制：解析 MX 主机，再解析其 A/AAAA，并可选 CIDR。*/
    private static boolean matchMX(InetAddress ip, String arg) throws TextParseException {
        String domain = arg;
        Integer cidr = null;

        Matcher m = Pattern.compile("^([^/]+)(?:/(\\d+))?$").matcher(arg);
        if (m.matches()) {
            domain = m.group(1);
            if (m.group(2) != null) cidr = Integer.parseInt(m.group(2));
        }

        // 查 MX
        Lookup l = new Lookup(domain, Type.MX);
        Record[] rs = l.run();
        if (rs == null) return false;

        List<String> mxHosts = new ArrayList<>();
        for (Record r : rs) {
            if (r instanceof MXRecord mx) {
                mxHosts.add(mx.getTarget().toString(true)); // 去掉尾部点
            }
        }

        for (String host : mxHosts) {
            for (InetAddress a : resolveA_AAAA(host)) {
                String base = a.getHostAddress() + "/" + (cidr != null ? cidr : (a.getAddress().length == 4 ? 32 : 128));
                if (cidrMatch(ip, base)) return true;
            }
        }
        return false;
    }

    private static List<InetAddress> resolveA_AAAA(String name) {
        List<InetAddress> out = new ArrayList<>();
        try {
            Lookup la = new Lookup(name, Type.A);
            Record[] ra = la.run();
            if (ra != null) {
                for (Record r : ra) {
                    if (r instanceof ARecord ar) out.add(ar.getAddress());
                }
            }
        } catch (TextParseException ignored) {}
        try {
            Lookup laaaa = new Lookup(name, Type.AAAA);
            Record[] raaaa = laaaa.run();
            if (raaaa != null) {
                for (Record r : raaaa) {
                    if (r instanceof AAAARecord ar) out.add(ar.getAddress());
                }
            }
        } catch (TextParseException ignored) {}
        return out;
    }
}

