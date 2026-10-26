package app.commentbubble;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Recognises a TikTok video from the texts visible on screen (creator, caption, sound).
 * Strings that show up on most screens (tab names, buttons) are learned and ignored.
 */
final class Matcher {
    private static final Pattern NO_LETTERS = Pattern.compile("^[^\\p{L}]*$");
    private static final Pattern COUNT = Pattern.compile("^[\\d.,\\s]+[kmb]?$");
    private static final int WARMUP_SCREENS = 15;
    private static final double COMMON_SHARE = 0.25;
    static final double MIN_SCORE = 0.5;
    static final int MIN_HITS = 2;

    private final Map<String, Integer> seen = new HashMap<>();
    private int screens;
    private Set<String> previous = new LinkedHashSet<>();

    static String normalize(CharSequence cs) {
        if (cs == null) return null;
        String s = cs.toString().replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        if (s.length() < 3 || s.length() > 300) return null;
        if (NO_LETTERS.matcher(s).matches() || COUNT.matcher(s).matches()) return null;
        return s;
    }

    /** Call once per scan. Only a changed screen counts towards the "common string" statistics. */
    void observe(Set<String> texts) {
        if (texts.equals(previous)) return;
        previous = texts;
        screens++;
        for (String s : texts) seen.merge(s, 1, Integer::sum);
    }

    boolean isCommon(String s) {
        if (screens < WARMUP_SCREENS) return false;
        Integer n = seen.get(s);
        return n != null && n > screens * COMMON_SHARE;
    }

    Set<String> distinctive(Set<String> texts) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : texts) if (!isCommon(s)) out.add(s);
        return out;
    }

    /** The video on screen, from TikTok's creator name, caption and sound views. */
    static final class VideoKey {
        final String creator;
        final String caption;
        final String sound;

        VideoKey(String creator, String caption, String sound) {
            this.creator = clean(creator);
            this.caption = clean(caption);
            this.sound = clean(sound);
        }

        boolean valid() {
            return creator != null && (caption != null || sound != null);
        }

        /** Captions can be cut off ("… mehr") at different lengths, so compare their start. */
        boolean sameVideo(VideoKey o) {
            if (o == null || creator == null || !creator.equals(o.creator)) return false;
            if (caption != null && o.caption != null) {
                int n = Math.min(40, Math.min(caption.length(), o.caption.length()));
                return n >= 3 && caption.regionMatches(0, o.caption, 0, n);
            }
            if (caption == null && o.caption == null) return sound != null && sound.equals(o.sound);
            return false;
        }

        @Override
        public String toString() {
            return creator + " – " + (caption != null ? caption : "(no caption)") + (sound != null ? " – " + sound : "");
        }

        static String clean(String s) {
            if (s == null) return null;
            s = s.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
            s = s.replaceAll("(\\s*(…|\\.\\.\\.)\\s*)?(mehr|more)$", "").replaceAll("(…|\\.\\.\\.)$", "").trim();
            return s.isEmpty() ? null : s;
        }
    }

    static final class Result {
        Store.Item item;
        double score;
        int hits;
    }

    /** Best saved item whose texts cover enough of what is on screen now. */
    Result best(Set<String> current, List<Store.Item> items) {
        Set<String> cur = distinctive(current);
        Result best = null;
        if (cur.size() < MIN_HITS) return null;
        for (Store.Item it : items) {
            int hits = 0;
            for (String s : cur) if (it.texts.contains(s)) hits++;
            double score = (double) hits / cur.size();
            if (hits >= MIN_HITS && score >= MIN_SCORE && (best == null || score > best.score)) {
                best = new Result();
                best.item = it;
                best.score = score;
                best.hits = hits;
            }
        }
        return best;
    }
}
