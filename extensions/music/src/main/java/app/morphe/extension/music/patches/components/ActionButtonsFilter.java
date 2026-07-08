/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.extension.music.patches.components;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.List;

import app.morphe.extension.music.settings.Settings;
import app.morphe.extension.shared.patches.components.BufferAsciiStrings;
import app.morphe.extension.shared.patches.components.ContextInterface;
import app.morphe.extension.shared.patches.components.Filter;
import app.morphe.extension.shared.patches.components.StringFilterGroup;

@SuppressWarnings("unused")
public final class ActionButtonsFilter extends Filter {

    /**
     * Injected by the patch onto the obfuscated litho message class that owns the button's
     * serialized proto buffer, so extension code can read it without walking obfuscated
     * field / method names.
     */
    public interface ButtonProtoBufferInterface {
        // Method body is added during patching.
        byte[] patch_getButtonProto();
    }

    private static final String VIDEO_ACTION_BAR_PREFIX = "video_action_bar.e";
    private static final String VIDEO_ACTION_BUTTON_WRAPPER_PREFIX = "video_action_button_with_vm_input.e";

    // Base names of the litho components / endpoint identifiers used to identify buttons.
    // Path callbacks append `.e` to bound the match to the litho identifier suffix.
    private static final String LIKE_BUTTON_MARKER = "like_button";
    private static final String DISLIKE_BUTTON_MARKER = "dislike_button";
    private static final String SEGMENTED_LIKE_DISLIKE_MARKER = "segmented_like_dislike_button";
    private static final String DOWNLOAD_MARKER = "download_button";
    private static final String MUSIC_DOWNLOAD_MARKER = "music_download_button";
    // Unique marker for the Download button in the parsed proto buffer - the litho EML
    // name `music_download_button` is a render-time artifact and is not present in the raw
    // proto stream that reaches the tree-node hook. Music's offline endpoint identifier
    // (`offlinelist`) is the only download-specific string that survives into the buffer.
    private static final String DOWNLOAD_PROTO_MARKER = "offlinelist";
    private static final String COMMENTS_MARKER = "music-comment-panel";
    private static final String LYRICS_MARKER = "music_watch_lyrics_panel";
    private static final String SHARE_MARKER = "timestamp_share_switch_button_entity_key";
    private static final String RADIO_MARKER = "RDAMVM";

    private final StringFilterGroup actionBar;
    private final StringFilterGroup genericActionButton;

    public ActionButtonsFilter() {
        actionBar = new StringFilterGroup(
                Settings.HIDE_ACTION_BAR,
                VIDEO_ACTION_BAR_PREFIX
        );
        addIdentifierCallbacks(actionBar);

        addPathCallbacks(
                new StringFilterGroup(
                        Settings.HIDE_LIKE_DISLIKE_BUTTON,
                        LIKE_BUTTON_MARKER + ".e",
                        DISLIKE_BUTTON_MARKER + ".e",
                        SEGMENTED_LIKE_DISLIKE_MARKER + ".e"
                ),
                new StringFilterGroup(
                        Settings.HIDE_DOWNLOAD_BUTTON,
                        DOWNLOAD_MARKER + ".e",
                        MUSIC_DOWNLOAD_MARKER + ".e"
                )
        );

        // Comments, Lyrics, Share, Save and Radio all render as generic `button.e` inside
        // `video_action_button_with_vm_input.e` - identity is dispatched inside isFiltered
        // from the button's ascii-string buffer.
        genericActionButton = new StringFilterGroup(
                null,
                VIDEO_ACTION_BUTTON_WRAPPER_PREFIX
        );
        addPathCallbacks(genericActionButton);
    }

    @Override
    public boolean isFiltered(ContextInterface contextInterface,
                              String identifier,
                              String accessibility,
                              String path,
                              byte[] buffer,
                              BufferAsciiStrings asciiStrings,
                              StringFilterGroup matchedGroup,
                              FilterContentType contentType,
                              int contentIndex) {
        if (matchedGroup == actionBar) {
            return true;
        }
        if (matchedGroup == genericActionButton) {
            if (path == null || !path.contains(VIDEO_ACTION_BAR_PREFIX)) {
                return false;
            }
            // Wrapper- and inner-level renders embed sibling data in their proto buffer, so
            // a single marker would match the whole action bar. Only fire on the leaf-button
            // render - path contains `|button.eml-fe|` but not the `button_inner` descendant.
            if (!path.contains("|button.eml-fe|") || path.contains("button_inner")) {
                return false;
            }
            if (asciiStrings == null) {
                return false;
            }
            // Dispatch by endpoint marker. Each of the four known buttons has a unique
            // endpoint string in its buffer. The Save button has no unique endpoint of its
            // own - its icon (`_playlist_add_`) also appears in every button's shared icon
            // catalogue - so it is resolved by elimination as the fall-through branch.
            String strings = asciiStrings.getStrings();
            if (strings.contains(COMMENTS_MARKER)) {
                return Settings.HIDE_COMMENTS_BUTTON.get();
            }
            if (strings.contains(LYRICS_MARKER)) {
                return Settings.HIDE_LYRICS_BUTTON.get();
            }
            if (strings.contains(SHARE_MARKER)) {
                return Settings.HIDE_SHARE_BUTTON.get();
            }
            if (strings.contains(RADIO_MARKER)) {
                return Settings.HIDE_RADIO_BUTTON.get();
            }
            return Settings.HIDE_SAVE_BUTTON.get();
        }
        return path != null && path.contains(VIDEO_ACTION_BAR_PREFIX);
    }

    /**
     * Injection point.
     * Physically removes entries from the action-bar tree-node list so Litho never allocates
     * their cells. Without this the litho identifier / path filters above only skip content
     * and leave empty gaps where hidden buttons used to be.
     * <p>
     * The whole-bar toggle clears the list outright; per-button hides pull each item's proto
     * payload via {@link #extractButtonProto} (patch-injected interface reached through a
     * reflection walk) and dispatch on the same endpoint markers that {@link #isFiltered}
     * uses for visual hiding.
     */
    public static void onLazilyConvertedElementLoaded(@NonNull String identifier,
                                                      @NonNull List<Object> treeNodeResultList) {
        if (!identifier.startsWith(VIDEO_ACTION_BAR_PREFIX)) {
            return;
        }
        if (Settings.HIDE_ACTION_BAR.get()) {
            treeNodeResultList.clear();
            return;
        }
        for (int i = treeNodeResultList.size() - 1; i >= 0; i--) {
            byte[] buttonProto = extractButtonProto(treeNodeResultList.get(i));
            if (buttonProto == null) continue;
            if (shouldHideButton(buttonProto)) {
                treeNodeResultList.remove(i);
            }
        }
    }

    /**
     * Extracts the button-specific proto byte payload from a tree-node item.
     * <p>
     * Recursively walks the reachable field graph of the item until it finds an object that
     * implements {@link ButtonProtoBufferInterface} (injected by the patch onto the obfuscated
     * litho message class), then delegates to the interface method. This keeps working when
     * ProGuard reshuffles field / method names because we identify the buffer holder by its
     * (patch-added) interface, not by an obfuscated name.
     */
    @Nullable
    private static byte[] extractButtonProto(@Nullable Object item) {
        if (item == null) return null;
        try {
            ButtonProtoBufferInterface holder = findButtonProtoHolder(item, /* depth */ 0,
                    /* maxDepth */ 6, new IdentityHashMap<>());
            return holder == null ? null : holder.patch_getButtonProto();
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Nullable
    private static ButtonProtoBufferInterface findButtonProtoHolder(
            @Nullable Object value, int depth, int maxDepth,
            IdentityHashMap<Object, Boolean> visited) {
        if (value == null || depth > maxDepth) return null;
        if (value instanceof ButtonProtoBufferInterface holder) return holder;
        if (value instanceof String || value instanceof Number || value instanceof Boolean
                || value instanceof byte[]) {
            return null;
        }
        Class<?> cls = value.getClass();
        // Skip framework types that either have no button data (locks / refs / collections
        // that would loop forever) or are unrelated to litho tree nodes.
        String pkg = cls.getName();
        if (pkg.startsWith("java.util.concurrent")
                || pkg.startsWith("java.lang.ref")
                || pkg.startsWith("java.io")
                || pkg.startsWith("android.")
                || pkg.startsWith("androidx.")
                || pkg.startsWith("j$.")) {
            return null;
        }
        if (visited.containsKey(value)) return null;
        visited.put(value, Boolean.TRUE);

        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    ButtonProtoBufferInterface found = findButtonProtoHolder(f.get(value),
                            depth + 1, maxDepth, visited);
                    if (found != null) return found;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean shouldHideButton(byte[] buffer) {
        String contents = new String(buffer, StandardCharsets.ISO_8859_1);
        // Order matters - the more-specific `segmented_like_dislike_button` and
        // `music_download_button` come before `like_button` / `download_button` so the
        // substring `like_button` inside `segmented_like_dislike_button` doesn't win.
        if (contents.contains(SEGMENTED_LIKE_DISLIKE_MARKER)
                || contents.contains(LIKE_BUTTON_MARKER)
                || contents.contains(DISLIKE_BUTTON_MARKER)) {
            return Settings.HIDE_LIKE_DISLIKE_BUTTON.get();
        }
        if (contents.contains(MUSIC_DOWNLOAD_MARKER)
                || contents.contains(DOWNLOAD_MARKER)
                || contents.contains(DOWNLOAD_PROTO_MARKER)) {
            return Settings.HIDE_DOWNLOAD_BUTTON.get();
        }
        if (contents.contains(COMMENTS_MARKER)) return Settings.HIDE_COMMENTS_BUTTON.get();
        if (contents.contains(LYRICS_MARKER)) return Settings.HIDE_LYRICS_BUTTON.get();
        if (contents.contains(SHARE_MARKER)) return Settings.HIDE_SHARE_BUTTON.get();
        if (contents.contains(RADIO_MARKER)) return Settings.HIDE_RADIO_BUTTON.get();
        // Save button has no unique endpoint marker of its own; it's the fall-through.
        return Settings.HIDE_SAVE_BUTTON.get();
    }
}
