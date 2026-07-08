/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 §7(b) and §7(c) terms that apply to this code.
 */

package app.morphe.patches.music.layout.hide.actionbuttons

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * Matches the `public final byte[] f()` accessor on the obfuscated litho message class
 * whose instances back each action-bar tree-node entry. The method has no parameters,
 * returns `[B` and delegates through `UpbMessage.jniEncode(JJ)[B` - a signature unique to
 * Google's litho/UpbMessage plumbing which stays stable across ProGuard reshuffles.
 */
internal object ButtonProtoBufferGetterFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "[B",
    parameters = listOf(),
    filters = listOf(
        methodCall(
            name = "jniEncode",
            opcode = Opcode.INVOKE_VIRTUAL,
        )
    )
)
