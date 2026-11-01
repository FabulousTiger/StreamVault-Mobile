package com.streamvault.feature.live.home

/** Automatic focus moves (sidebar reveal, focus restore) never pull focus out of a focused text field. */
fun shouldAutoMoveLiveFocus(textInputFocused: Boolean): Boolean = !textInputFocused
