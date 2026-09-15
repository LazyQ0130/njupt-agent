package com.njupt.aiassistant.service.retrieval;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record TransferQueryIntent(
        String sourceMajor,
        String targetMajor
) {

    private static final String SUBJECT =
            "[\\p{IsHan}A-Za-z0-9（）()·+\\-]{2,24}?";
    private static final String REQUEST =
            "(?:(?:有?什么|需要什么)?(?:条件|要求|名额|考核)|"
                    + "考什么|怎么考|怎么转|可以吗|是否可以|能不能|申请)";
    private static final Pattern FROM_TO = Pattern.compile(
            "(?:请问|想问|我想)?(?:从)?(?<source>" + SUBJECT + ")"
                    + "(?:专业)?转(?:入|到|去)"
                    + "(?<target>" + SUBJECT + ")(?:专业|学院)?"
                    + "(?:的)?" + REQUEST
    );
    private static final Pattern EXPLICIT_FROM_TO = Pattern.compile(
            "(?:请问|想问|我想)?从(?<source>" + SUBJECT + ")"
                    + "(?:专业)?转(?:入|到|去)"
                    + "(?<target>" + SUBJECT + ")(?:专业|学院)?"
                    + "(?:可以吗|行吗|吗|[?？]|$)"
    );
    private static final Pattern COMPACT_FROM_TO = Pattern.compile(
            "(?:请问|想问|我想)?(?<source>" + SUBJECT + ")(?:专业)?转"
                    + "(?<target>" + SUBJECT + ")(?:专业|学院)?"
                    + "(?:的)?" + REQUEST
    );
    private static final Pattern TARGET_RECEIVE = Pattern.compile(
            "(?<target>" + SUBJECT + ")(?:专业|学院)?"
                    + "(?:接收转专业|转专业接收)" + ".*"
                    + "(?:" + REQUEST + "|学生|吗)"
    );
    private static final Pattern TRANSFER_INTO = Pattern.compile(
            "转入(?<target>" + SUBJECT + ")(?:专业|学院)?"
                    + "(?:的)?" + REQUEST
    );
    private static final Pattern TARGET_TRANSFER = Pattern.compile(
            "(?<target>" + SUBJECT + ")(?:专业|学院)?转专业"
                    + "(?:" + REQUEST + "|政策|细则)"
    );
    private static final String TRANSFER_DETAIL =
            "(?:名额|笔试|面试|考核方式)";
    private static final Pattern TARGET_DETAIL_FOLLOW_UP = Pattern.compile(
            "(?<target>" + SUBJECT + ")(?:专业|学院)?(?:的)?"
                    + TRANSFER_DETAIL
                    + "(?:[、，,和及/]|还有|以及).*"
                    + TRANSFER_DETAIL
    );
    private static final Set<String> NON_TARGETS = Set.of(
            "南邮",
            "南京邮电大学",
            "学校",
            "本科生",
            "学生"
    );
    private static final Set<String> EXCLUDED_PHRASES = Set.of(
            "转账",
            "跳转",
            "转发",
            "转接",
            "转让"
    );

    public static Optional<TransferQueryIntent> analyze(String question) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        var normalized = question
                .replaceAll("\\s+", "")
                .replace('？', '?')
                .replace('，', ',');
        if (EXCLUDED_PHRASES.stream().anyMatch(normalized::contains)) {
            return Optional.empty();
        }

        var matched = match(normalized, FROM_TO, true);
        if (matched.isPresent()) {
            return matched;
        }
        matched = match(normalized, EXPLICIT_FROM_TO, true);
        if (matched.isPresent()) {
            return matched;
        }
        matched = match(normalized, COMPACT_FROM_TO, true);
        if (matched.isPresent()) {
            return matched;
        }
        matched = match(normalized, TARGET_RECEIVE, false);
        if (matched.isPresent()) {
            return matched;
        }
        matched = match(normalized, TRANSFER_INTO, false);
        if (matched.isPresent()) {
            return matched;
        }
        matched = match(normalized, TARGET_TRANSFER, false);
        if (matched.isPresent()) {
            return matched;
        }
        return match(normalized, TARGET_DETAIL_FOLLOW_UP, false);
    }

    public String retrievalQuestion(String originalQuestion) {
        var source = sourceMajor == null
                ? "未指定"
                : sourceMajor;
        return """
                原问题：%s
                转专业目标：%s
                来源专业：%s
                检索重点：%s专业或所属学院当期转专业接收条件、专业要求、\
                名额、学业表现、笔试、面试和考核办法；补充学校统一转专业\
                规定；仅在存在明确转出限制时参考%s的转出规则。
                """.formatted(
                originalQuestion.strip(),
                targetMajor,
                source,
                targetMajor,
                source
        ).strip();
    }

    private static Optional<TransferQueryIntent> match(
            String question,
            Pattern pattern,
            boolean hasSource
    ) {
        Matcher matcher = pattern.matcher(question);
        if (!matcher.find()) {
            return Optional.empty();
        }
        var target = cleanSubject(matcher.group("target"));
        var source = hasSource
                ? cleanSubject(matcher.group("source"))
                : null;
        if (target.isBlank() || NON_TARGETS.contains(target)) {
            return Optional.empty();
        }
        if (source != null && source.equals(target)) {
            source = null;
        }
        return Optional.of(new TransferQueryIntent(source, target));
    }

    private static String cleanSubject(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replaceFirst("^(请问|想问|我想|能否|可以)", "")
                .replaceFirst("(有什么|什么|需要什么)$", "")
                .replaceFirst("(专业|学院)$", "")
                .strip();
    }
}
