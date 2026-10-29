package app.kawkab.android.accessibility

import android.view.accessibility.AccessibilityNodeInfo
import app.kawkab.android.domain.safety.TapTarget

/*
 * Copyright (c) 2026 OpenDroid Contributors
 * Licensed under the Apache License, Version 2.0.
 * Adapted for Kawkab from yashab-cyber/opendroid @ c08175cb815344da038f92c80e301929b28d79e8.
 */

/**
 * Walks an accessibility tree. Callers recycle the root they passed in.
 * A click is dispatched only when [allow] says the target may be tapped.
 */
class AccessibilityNodeTraversal {
    /**
     * Text in the tree, and whether any node is a password field.
     * One walk, so the nodes are recycled only once.
     */
    fun describe(root: AccessibilityNodeInfo?): Pair<String, Boolean> {
        if (root == null) return "" to false
        val builder = StringBuilder()
        val password = collectText(root, builder)
        return builder.toString().trim() to password
    }

    /**
     * Text and content descriptions in the tree, one node per line.
     */
    fun screenText(root: AccessibilityNodeInfo?): String = describe(root).first

    /**
     * Clicks the first node whose text contains [text], when [allow] accepts it.
     */
    fun findAndClick(root: AccessibilityNodeInfo?, text: String, allow: (TapTarget) -> Boolean): Boolean {
        if (root == null || text.isBlank()) return false
        val nodes = root.findAccessibilityNodeInfosByText(text)
        for (node in nodes) {
            val clicked = clickNode(node, allow)
            node.recycle()
            if (clicked) return true
        }
        return false
    }

    /**
     * Clicks the first node with [viewId], when [allow] accepts it.
     */
    fun findAndClickById(root: AccessibilityNodeInfo?, viewId: String, allow: (TapTarget) -> Boolean): Boolean {
        if (root == null || viewId.isBlank()) return false
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        for (node in nodes) {
            val clicked = clickNode(node, allow)
            node.recycle()
            if (clicked) return true
        }
        return false
    }

    /**
     * Writes [content] into the first editable node whose text contains [searchText].
     */
    fun findAndType(root: AccessibilityNodeInfo?, searchText: String, content: String): Boolean {
        if (root == null) return false
        val nodes = root.findAccessibilityNodeInfosByText(searchText)
        for (node in nodes) {
            val typed = typeInto(node, content)
            node.recycle()
            if (typed) return true
        }
        return false
    }

    private fun clickNode(node: AccessibilityNodeInfo, allow: (TapTarget) -> Boolean): Boolean {
        val clickable = clickableAncestor(node) ?: return false
        val target = TapTarget(
            text = clickable.text?.toString(),
            contentDescription = clickable.contentDescription?.toString(),
            viewId = clickable.viewIdResourceName,
        )
        if (!allow(target)) return false
        return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isClickable) return node
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) return parent
            parent = parent.parent
        }
        return null
    }

    private fun typeInto(node: AccessibilityNodeInfo, content: String): Boolean {
        if (!node.isEditable) return false
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, content)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun collectText(node: AccessibilityNodeInfo, builder: StringBuilder): Boolean {
        val text = node.text?.toString()
        val description = node.contentDescription?.toString()
        when {
            !text.isNullOrEmpty() -> builder.append(text).append('\n')
            !description.isNullOrEmpty() -> builder.append(description).append('\n')
        }
        var password = node.isPassword
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            password = collectText(child, builder) || password
            child.recycle()
        }
        return password
    }
}
