package com.akergez.shardkeep.platform

/**
 * Whether this platform is driven by a fine pointer — a mouse or a trackpad — rather than
 * by touch.
 *
 * Layout normally has no business asking what it is running on: a window's width says what
 * fits, and that is the question worth asking. This is the exception, because width does not
 * predict what is pointing at the screen. A large tablet in landscape is as wide as a laptop
 * yet is still operated by a finger, and controls sized for a cursor fall below the touch
 * target a finger needs. Ask this only where the pointer itself is the thing that matters,
 * and keep sizing decisions on the window width everywhere else.
 */
expect fun hasFinePointer(): Boolean
