package com.donkolia.santumrandomspawns.conditions;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Evaluates {@code conditions_to_spawn} lines using ConditionalEvents-compatible syntax:
 * supports {@code OR} / {@code AND} (case-insensitive), PlaceholderAPI placeholders from all plugins,
 * and operators {@code ==}, {@code !=}, {@code >=}, {@code <=}, {@code >}, {@code <},
 * {@code equals}, {@code !equals}, {@code equalsIgnoreCase}, {@code !equalsIgnoreCase},
 * {@code startsWith}, {@code !startsWith}, {@code contains}, {@code !contains},
 * {@code endsWith}, {@code !endsWith}, {@code matches}, {@code !matches}.
 */
public final class ConditionEvaluator {

    private static final Pattern OR_SPLIT = Pattern.compile("(?i)\\s+or\\s+");
    private static final Pattern AND_SPLIT = Pattern.compile("(?i)\\s+and\\s+");

    private enum Operator {
        EQUALS("=="),
        NOT_EQUALS("!="),
        GREATER_EQUALS(">="),
        LOWER_EQUALS("<="),
        GREATER(">"),
        LOWER("<"),
        EQUALS_LEGACY("equals"),
        NOT_EQUALS_LEGACY("!equals"),
        EQUALS_IGNORE_CASE("equalsIgnoreCase"),
        NOT_EQUALS_IGNORE_CASE("!equalsIgnoreCase"),
        STARTS_WITH("startsWith"),
        NOT_STARTS_WITH("!startsWith"),
        CONTAINS("contains"),
        NOT_CONTAINS("!contains"),
        ENDS_WITH("endsWith"),
        NOT_ENDS_WITH("!endsWith"),
        MATCHES("matches"),
        NOT_MATCHES("!matches");

        final String token;

        Operator(String token) {
            this.token = token;
        }
    }

    private final boolean placeholderApiPresent;

    public ConditionEvaluator() {
        this.placeholderApiPresent = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public boolean evaluateAll(List<String> conditionLines, Player player, Location spawnLocation) {
        if (conditionLines == null || conditionLines.isEmpty()) {
            return true;
        }
        for (String line : conditionLines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (!evaluateLine(line.trim(), player, spawnLocation)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateLine(String line, Player player, Location spawnLocation) {
        String[] orBranches = OR_SPLIT.split(line);
        for (String orBranch : orBranches) {
            String[] andTerms = AND_SPLIT.split(orBranch.trim());
            boolean allTrue = true;
            for (String term : andTerms) {
                if (!evaluateTerm(term.trim(), player, spawnLocation)) {
                    allTrue = false;
                    break;
                }
            }
            if (allTrue) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateTerm(String term, Player player, Location spawnLocation) {
        for (Operator op : Operator.values()) {
            String spaced = " " + op.token + " ";
            int idx = term.indexOf(spaced);
            if (idx < 0) {
                continue;
            }
            String rawLeft = term.substring(0, idx).trim();
            String rawRight = term.substring(idx + spaced.length()).trim();

            String left = resolvePlaceholders(rawLeft, player, spawnLocation);
            String right = resolvePlaceholders(rawRight, player, spawnLocation);

            return compare(left, right, op);
        }
        return false;
    }

    private String resolvePlaceholders(String input, Player player, Location spawnLocation) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String result = input;
        if (player != null) {
            if (placeholderApiPresent) {
                result = PlaceholderAPI.setPlaceholders(player, result);
            }
            result = result
                    .replace("%player_level%", String.valueOf(player.getLevel()))
                    .replace("%player_name%", player.getName())
                    .replace("%player_world%", player.getWorld().getName())
                    .replace("%player_x%", String.valueOf(player.getLocation().getBlockX()))
                    .replace("%player_y%", String.valueOf(player.getLocation().getBlockY()))
                    .replace("%player_z%", String.valueOf(player.getLocation().getBlockZ()));
        }
        if (spawnLocation != null && spawnLocation.getWorld() != null) {
            result = result
                    .replace("%world_name%", spawnLocation.getWorld().getName())
                    .replace("%world_time%", String.valueOf(spawnLocation.getWorld().getTime()))
                    .replace("%spawn_x%", String.valueOf(spawnLocation.getBlockX()))
                    .replace("%spawn_y%", String.valueOf(spawnLocation.getBlockY()))
                    .replace("%spawn_z%", String.valueOf(spawnLocation.getBlockZ()));
        }
        return result;
    }

    private boolean compare(String left, String right, Operator op) {
        String leftLower = left.toLowerCase(Locale.ROOT);
        String rightLower = right.toLowerCase(Locale.ROOT);

        Double leftNum = tryParseDouble(left);
        Double rightNum = tryParseDouble(right);

        return switch (op) {
            case EQUALS, EQUALS_LEGACY -> {
                if (leftNum != null && rightNum != null) {
                    yield Double.compare(leftNum, rightNum) == 0;
                }
                yield left.equals(right);
            }
            case NOT_EQUALS, NOT_EQUALS_LEGACY -> {
                if (leftNum != null && rightNum != null) {
                    yield Double.compare(leftNum, rightNum) != 0;
                }
                yield !left.equals(right);
            }
            case EQUALS_IGNORE_CASE -> left.equalsIgnoreCase(right);
            case NOT_EQUALS_IGNORE_CASE -> !left.equalsIgnoreCase(right);
            case STARTS_WITH -> leftLower.startsWith(rightLower);
            case NOT_STARTS_WITH -> !leftLower.startsWith(rightLower);
            case CONTAINS -> leftLower.contains(rightLower);
            case NOT_CONTAINS -> !leftLower.contains(rightLower);
            case ENDS_WITH -> leftLower.endsWith(rightLower);
            case NOT_ENDS_WITH -> !leftLower.endsWith(rightLower);
            case MATCHES -> leftLower.matches(rightLower);
            case NOT_MATCHES -> !leftLower.matches(rightLower);
            case GREATER -> leftNum != null && rightNum != null && leftNum > rightNum;
            case GREATER_EQUALS -> leftNum != null && rightNum != null && leftNum >= rightNum;
            case LOWER -> leftNum != null && rightNum != null && leftNum < rightNum;
            case LOWER_EQUALS -> leftNum != null && rightNum != null && leftNum <= rightNum;
        };
    }

    private Double tryParseDouble(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
